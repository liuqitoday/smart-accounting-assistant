package com.liuqitech.accountingassistant.service;

import com.liuqitech.accountingassistant.dto.ImportResult;
import com.liuqitech.accountingassistant.dto.PageResponseDto;
import com.liuqitech.accountingassistant.dto.SaveTransactionRequest;
import com.liuqitech.accountingassistant.dto.TransactionAndCategoryParseResult;
import com.liuqitech.accountingassistant.dto.TransactionDto;
import com.liuqitech.accountingassistant.dto.TransactionParseRequest;
import com.liuqitech.accountingassistant.dto.TransactionParseResponse;
import com.liuqitech.accountingassistant.dto.UpdateTransactionRequest;
import com.liuqitech.accountingassistant.entity.Account;
import com.liuqitech.accountingassistant.entity.Category;
import com.liuqitech.accountingassistant.entity.Tag;
import com.liuqitech.accountingassistant.entity.Transaction;
import com.liuqitech.accountingassistant.enums.TransactionType;
import com.liuqitech.accountingassistant.exception.BusinessException;
import com.liuqitech.accountingassistant.exception.ResourceNotFoundException;
import com.liuqitech.accountingassistant.mapper.TransactionMapper;
import com.liuqitech.accountingassistant.repository.AccountRepository;
import com.liuqitech.accountingassistant.repository.TransactionRepository;
import com.liuqitech.accountingassistant.repository.specification.TransactionSpecifications;
import com.liuqitech.accountingassistant.util.AppClock;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 交易服务类。
 *
 * <p>注意：类上不加 @Transactional——解析类方法内的 AI 调用耗时可达数十秒甚至更久，
 * SQLite 单写者模型下不能让长事务占住池连接；只有真正写库的方法开方法级短事务
 * （同 {@link AnalysisChatService} 的做法）。</p>
 */
@Service
public class TransactionService {

    private static final Logger logger = LoggerFactory.getLogger(TransactionService.class);

    private final TransactionRepository transactionRepository;
    private final AIParserService aiParserService;
    private final CategoryService categoryService;
    private final CategoryCorrectionService categoryCorrectionService;
    private final TagService tagService;
    private final AccountService accountService;
    private final AccountRepository accountRepository;
    private final TransactionMapper transactionMapper;
    private final TransactionTemplate transactionTemplate;
    private final String configuredAiModelName;

    public TransactionService(TransactionRepository transactionRepository,
                              AIParserService aiParserService,
                              CategoryService categoryService,
                              CategoryCorrectionService categoryCorrectionService,
                              TagService tagService,
                              AccountService accountService,
                              AccountRepository accountRepository,
                              TransactionMapper transactionMapper,
                              TransactionTemplate transactionTemplate,
                              @Value("${spring.ai.openai.chat.options.model:gpt-3.5-turbo}") String configuredAiModelName) {
        this.transactionRepository = transactionRepository;
        this.aiParserService = aiParserService;
        this.categoryService = categoryService;
        this.categoryCorrectionService = categoryCorrectionService;
        this.tagService = tagService;
        this.accountService = accountService;
        this.accountRepository = accountRepository;
        this.transactionMapper = transactionMapper;
        this.transactionTemplate = transactionTemplate;
        this.configuredAiModelName = configuredAiModelName;
    }
    
    /**
     * 仅解析交易文本（不保存）。
     * <p>不加事务：AI 调用（可长达数十秒）在无事务上下文执行；纠正记忆命中计数等
     * 写库动作由 {@link CategoryCorrectionService} 自身的方法级事务以短事务完成。</p>
     */
    public TransactionParseResponse parseTransactionOnly(TransactionParseRequest request, Long ledgerId) {
        logger.debug("解析交易文本: {} (ledger={})", request, ledgerId);
        String text = request.getText();

        ResolvedParse resolved = resolveTransactionFromText(text, ledgerId);
        TransactionAndCategoryParseResult parseResult = resolved.parseResult();
        Category category = resolved.category();

        TransactionParseResponse response = new TransactionParseResponse();
        response.setAmount(parseResult.getAmount());
        response.setType(parseResult.getType());
        response.setDescription(parseResult.getDescription());
        response.setOriginalText(text);
        response.setCategoryId(category.getId());
        response.setCategoryName(category.getName());

        if (category.getParent() != null) {
            response.setParentCategoryId(category.getParent().getId());
            response.setParentCategoryName(category.getParent().getName());
        }

        response.setTransactionDate(parseResult.getTransactionDate());
        response.setParsedMerchant(parseResult.getMerchant());
        response.setConfidenceScore(resolved.learnedFromHistory() ? LEARNED_CONFIDENCE : parseResult.getConfidence());
        response.setAiModelUsed(resolved.fallbackUsed() ? null : configuredAiModelName);
        response.setNote(parseResult.getNote());
        response.setRelatedUser(parseResult.getRelatedUser());

        // 记录「系统这一刻建议的分类」，保存时据此判断用户是否手动改了分类
        response.setAiSuggestedCategoryId(category.getId());
        response.setLearnedFromHistory(resolved.learnedFromHistory());
        response.setFallbackUsed(resolved.fallbackUsed());

        return response;
    }

    /**
     * 解析并创建交易记录。
     * <p>不加事务：AI 调用在无事务上下文执行；落库段经 {@link TransactionTemplate}
     * 显式短事务完成（同类内 this.createTransaction() 不走代理，方法级事务注解
     * 对自调用不生效，故用编程式事务包裹）。</p>
     */
    public TransactionParseResponse parseAndCreateTransaction(TransactionParseRequest request, Long ledgerId, String username) {
        logger.debug("解析并创建交易记录: {} (ledger={})", request, ledgerId);
        String text = request.getText();

        // 1. 与 parse-only 共用同一条「AI 解析→纠正记忆→关键词回退」管线
        ResolvedParse resolved = resolveTransactionFromText(text, ledgerId);
        TransactionAndCategoryParseResult parseResult = resolved.parseResult();
        Category category = resolved.category();

        // 2. 通过统一的普通交易写入入口创建，复用类型/分类/账户不变量校验
        TransactionCreationCommand command = new TransactionCreationCommand();
        command.setLedgerId(ledgerId);
        command.setUsername(username);
        command.setAmount(parseResult.getAmount());
        command.setType(parseResult.getType());
        command.setDescription(parseResult.getDescription());
        command.setOriginalText(text);
        command.setCategoryId(category.getId());
        command.setTransactionDate(parseResult.getTransactionDate());
        command.setParsedMerchant(parseResult.getMerchant());
        command.setConfidenceScore(resolved.learnedFromHistory() ? LEARNED_CONFIDENCE : parseResult.getConfidence());
        command.setAiModelUsed(resolved.fallbackUsed() ? null : configuredAiModelName);
        command.setNote(parseResult.getNote());
        command.setRelatedUser(parseResult.getRelatedUser());

        // 3. 落库进短事务
        Transaction savedTransaction = transactionTemplate.execute(status -> createTransaction(command));

        logger.info("交易记录创建成功: {}", savedTransaction);

        // 4. 转换为响应DTO
        TransactionParseResponse response = transactionMapper.toParseResponse(savedTransaction);
        response.setFallbackUsed(resolved.fallbackUsed());
        return response;
    }

    /** 纠正记忆命中时对外报告的置信度 */
    private static final BigDecimal LEARNED_CONFIDENCE = new BigDecimal("0.99");

    /** 解析管线结果：抽取字段 + 解析出的分类 + 是否命中账本纠正记忆 */
    private record ResolvedParse(TransactionAndCategoryParseResult parseResult,
                                 Category category,
                                 boolean learnedFromHistory,
                                 boolean fallbackUsed) {
    }

    /** 原始解析结果及是否发生了 AI 降级。 */
    private record ParseAttempt(TransactionAndCategoryParseResult parseResult,
                                boolean fallbackUsed) {
    }

    /**
     * parse-only 与 parse+create 共用的解析管线：
     * AI 合并解析（失败回退规则抽取）→ 账本纠正记忆 → AI 分类校验 → 关键词规则回退。
     */
    private ResolvedParse resolveTransactionFromText(String text, Long ledgerId) {
        // 1. 一次 AI 调用同时完成抽取 + 分类（合并原两次调用，省一次网络往返）
        ParseAttempt attempt = doAIParseWithFallback(text);
        TransactionAndCategoryParseResult parseResult = attempt.parseResult();

        // 2. 分类解析：先查账本纠正记忆，命中则覆盖；否则用 AI 分类（校验）；最后回退规则
        boolean learnedFromHistory = false;
        Optional<Category> categoryOpt = categoryCorrectionService.findCorrectedCategory(
                ledgerId, parseResult.getType(), parseResult.getMerchant(), parseResult.getDescription());
        if (categoryOpt.isPresent() && categoryOpt.get().getType() != parseResult.getType()) {
            logger.warn("忽略类型不匹配的历史分类纠正: categoryId={}, categoryType={}, transactionType={}",
                    categoryOpt.get().getId(), categoryOpt.get().getType(), parseResult.getType());
            categoryOpt = Optional.empty();
        }
        if (categoryOpt.isPresent()) {
            learnedFromHistory = true;
            logger.debug("命中历史纠正记忆，采用分类: {}", categoryOpt.get().getName());
        } else if (parseResult.getCategoryId() != null) {
            categoryOpt = categoryService.resolveAndValidateCategory(
                    parseResult.getCategoryId(), parseResult.getParentCategoryId(), parseResult.getType());
        }
        if (categoryOpt.isEmpty()) {
            categoryOpt = categoryService.findCategoryWithRules(
                    parseResult.getDescription(), parseResult.getType());
        }

        Category category = categoryOpt
                .orElseThrow(() -> new BusinessException("无法找到合适的分类"));
        return new ResolvedParse(parseResult, category, learnedFromHistory, attempt.fallbackUsed());
    }

    /**
     * 保存已解析的交易记录
     */
    @Transactional
    public TransactionParseResponse saveTransaction(SaveTransactionRequest request, Long ledgerId, String username) {
        logger.debug("保存交易记录: {} (ledger={})", request, ledgerId);

        TransactionCreationCommand command = new TransactionCreationCommand();
        command.setLedgerId(ledgerId);
        command.setUsername(username);
        command.setAmount(request.getAmount());
        command.setType(request.getType());
        command.setDescription(request.getDescription());
        command.setOriginalText(request.getOriginalText());
        command.setCategoryId(request.getCategoryId());
        command.setTransactionDate(request.getTransactionDate());
        command.setParsedMerchant(request.getParsedMerchant());
        command.setConfidenceScore(request.getConfidenceScore());
        command.setAiModelUsed(request.getAiModelUsed());
        command.setNote(request.getNote());
        command.setRelatedUser(request.getRelatedUser());
        command.setAccountId(request.getAccountId());

        Transaction savedTransaction = createTransaction(command);
        logger.info("交易记录保存成功: {}", savedTransaction);

        // 捕获分类纠正：用户最终分类与系统初次建议不一致 = 用户手动改了分类，记入账本纠正记忆
        Long aiSuggestedCategoryId = request.getAiSuggestedCategoryId();
        Long finalCategoryId = request.getCategoryId();
        if (aiSuggestedCategoryId != null && finalCategoryId != null
                && !aiSuggestedCategoryId.equals(finalCategoryId)) {
            categoryCorrectionService.recordCorrection(
                    ledgerId,
                    username,
                    request.getType(),
                    request.getParsedMerchant(),
                    request.getDescription(),
                    request.getOriginalText(),
                    finalCategoryId,
                    savedTransaction.getCategory(),
                    aiSuggestedCategoryId);
        }

        return transactionMapper.toParseResponse(savedTransaction);
    }

    /**
     * 创建普通交易的内部复用入口。手动记账、周期账单等写入路径都走这里，
     * 避免重复实现分类快照、账户校验和标签关联。
     */
    @Transactional
    public Transaction createTransaction(TransactionCreationCommand command) {
        Category category = requireRegularCategory(command.getCategoryId(), command.getType());

        Transaction transaction = new Transaction();
        transaction.setAmount(command.getAmount());
        transaction.setType(command.getType());
        transaction.setDescription(command.getDescription());
        transaction.setOriginalText(command.getOriginalText());
        applyCategory(transaction, category);
        transaction.setTransactionDate(command.getTransactionDate());
        transaction.setParsedMerchant(command.getParsedMerchant());
        transaction.setParsedAmount(command.getAmount());
        transaction.setConfidenceScore(command.getConfidenceScore());
        transaction.setAiModelUsed(command.getAiModelUsed());
        transaction.setNote(command.getNote());
        transaction.setRelatedUser(command.getRelatedUser());
        transaction.setLedgerId(command.getLedgerId());
        transaction.setCreatedBy(command.getUsername());
        transaction.setRecurringBillId(command.getRecurringBillId());
        transaction.setRecurringOccurrenceDate(command.getRecurringOccurrenceDate());
        transaction.setCounterAccountId(null);
        applyAccount(transaction, command.getAccountId(), command.getLedgerId());
        transaction.setTags(command.getTags());
        return transactionRepository.save(transaction);
    }
    
    /**
     * 获取交易记录列表（按账本过滤）
     */
    @Transactional(readOnly = true)
    public PageResponseDto<TransactionDto> getTransactions(Long ledgerId, Pageable pageable) {
        logger.debug("获取账本 {} 的交易记录列表", ledgerId);

        // 使用 Specification 动态查询
        Specification<Transaction> spec = TransactionSpecifications.byLedger(ledgerId);

        // 如果没有指定排序，默认按 ID 降序
        if (!pageable.getSort().isSorted()) {
            pageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                    Sort.by(Sort.Direction.DESC, "id"));
        }

        Page<Transaction> transactions = transactionRepository.findAll(spec, pageable);
        return toPageResponse(transactions);
    }

    /**
     * 根据ID获取交易记录（限定当前账本，跨账本视为不存在）
     */
    @Transactional(readOnly = true)
    public Optional<TransactionDto> getTransactionById(Long id, Long ledgerId) {
        logger.debug("根据ID获取交易记录: {} (ledger={})", id, ledgerId);
        return transactionRepository.findById(id)
                .filter(t -> ledgerId.equals(t.getLedgerId()))
                .map(transactionMapper::toDto);
    }

    /**
     * 更新交易记录（限定当前账本；角色权限由拦截器保证为 EDITOR 及以上）
     */
    @Transactional
    public Optional<TransactionDto> updateTransaction(Long id, UpdateTransactionRequest request, Long ledgerId) {
        logger.debug("账本 {} 更新交易记录: {} -> {}", ledgerId, id, request);

        Optional<Transaction> transaction = transactionRepository.findById(id);
        if (transaction.isEmpty() || !ledgerId.equals(transaction.get().getLedgerId())) {
            return Optional.empty();
        }

        return transaction.map(existingTransaction -> {
            if (existingTransaction.getType() == TransactionType.TRANSFER) {
                throw new BusinessException("转账不能通过普通交易接口修改，请使用转账专用功能");
            }

            Category managedCategory = requireRegularCategory(request.getCategoryId(), request.getType());
            updateExistingTransaction(existingTransaction, request, managedCategory);
            applyAccount(existingTransaction, request.getAccountId(), ledgerId);
            existingTransaction.setCounterAccountId(null);
            Transaction savedTransaction = transactionRepository.save(existingTransaction);
            logger.info("交易记录更新成功: {}", savedTransaction);
            return transactionMapper.toDto(savedTransaction);
        });
    }

    /**
     * 删除交易记录（软删除，限定当前账本；角色权限由拦截器保证为 EDITOR 及以上）
     */
    @Transactional
    public boolean deleteTransaction(Long id, Long ledgerId) {
        logger.debug("账本 {} 软删除交易记录: {}", ledgerId, id);

        Optional<Transaction> transactionOpt = validateTransactionAccess(id, ledgerId);
        if (transactionOpt.isEmpty()) {
            return false;
        }

        Transaction t = transactionOpt.get();
        if (t.getDeletedAt() != null) {
            logger.warn("交易记录 {} 已被删除", id);
            return false;
        }

        t.setDeletedAt(AppClock.now());
        transactionRepository.save(t);
        logger.info("交易记录软删除成功: {}", id);
        return true;
    }

    /**
     * 恢复已删除的交易记录（撤销删除）
     */
    @Transactional
    public boolean restoreTransaction(Long id, Long ledgerId) {
        logger.debug("账本 {} 恢复交易记录: {}", ledgerId, id);

        Optional<Transaction> transactionOpt = validateTransactionAccess(id, ledgerId);
        if (transactionOpt.isEmpty()) {
            return false;
        }

        Transaction t = transactionOpt.get();
        if (t.getDeletedAt() == null) {
            logger.warn("交易记录 {} 未被删除，无需恢复", id);
            return false;
        }

        t.setDeletedAt(null);
        transactionRepository.save(t);
        logger.info("交易记录恢复成功: ", id);
        return true;
    }

    /**
     * 校验交易记录访问权限（限定当前账本）
     */
    private Optional<Transaction> validateTransactionAccess(Long id, Long ledgerId) {
        return transactionRepository.findById(id)
                .filter(t -> ledgerId.equals(t.getLedgerId()));
    }
    
    /**
     * 根据日期范围获取交易记录（按账本过滤）
     */
    @Transactional(readOnly = true)
    public PageResponseDto<TransactionDto> getTransactionsByDateRange(Long ledgerId, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        logger.debug("获取账本 {} 在日期范围 {} - {} 的交易记录", ledgerId, startDate, endDate);

        // 使用 Specification 动态查询
        Specification<Transaction> spec = TransactionSpecifications.byLedger(ledgerId)
                .and(TransactionSpecifications.byDateRange(startDate, endDate));

        // 如果没有指定排序，默认按 ID 降序
        if (!pageable.getSort().isSorted()) {
            pageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                    Sort.by(Sort.Direction.DESC, "id"));
        }

        Page<Transaction> transactions = transactionRepository.findAll(spec, pageable);
        return toPageResponse(transactions);
    }

    /**
     * 根据筛选条件查询交易记录（带分页，支持关键词搜索）
     * 使用 JPA Specification 实现动态查询
     */
    @Transactional(readOnly = true)
    public PageResponseDto<TransactionDto> queryTransactions(Long ledgerId, String keyword, TransactionType type, LocalDate startDate, LocalDate endDate, List<Long> tagIds, String createdBy, Long categoryId, Long accountId, String merchant, BigDecimal minAmount, BigDecimal maxAmount, Pageable pageable) {
        logger.debug("查询账本 {} 的交易记录，筛选条件 - 关键词: {}, 类型: {}, 日期范围: {} - {}, 标签: {}, 创建人: {}, 分类: {}, 账户: {}, 商家: {}, 金额: {} - {}",
                ledgerId, keyword, type, startDate, endDate, tagIds, createdBy, categoryId, accountId, merchant, minAmount, maxAmount);

        validateQueryFilters(ledgerId, accountId, minAmount, maxAmount);
        Specification<Transaction> spec = TransactionSpecifications.buildQuery(
                ledgerId, keyword, type, startDate, endDate, tagIds, createdBy, categoryId,
                accountId, merchant, minAmount, maxAmount);

        // 如果没有指定排序，默认按 ID 降序
        if (!pageable.getSort().isSorted()) {
            pageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                    Sort.by(Sort.Direction.DESC, "id"));
        }

        Page<Transaction> transactions = transactionRepository.findAll(spec, pageable);
        return toPageResponse(transactions);
    }

    /**
     * 导出账本所有交易记录为CSV
     */
    @Transactional(readOnly = true)
    public String exportTransactionsToCSV(Long ledgerId) {
        logger.debug("导出账本 {} 的所有交易记录", ledgerId);
        // JOIN FETCH tags，避免遍历 t.getTags() 时逐条懒加载 N+1
        List<Transaction> transactions = transactionRepository.findAllWithTagsByLedgerIdOrderByIdDesc(ledgerId);
        return buildCSV(transactions, accountService.getAccountNameMap(ledgerId));
    }

    /**
     * 导出账本交易记录为CSV（带筛选条件）
     * <p>无任何筛选时走 1 参 JOIN FETCH 全量路径，单次查询取交易 + tags；
     * 有筛选时走 Specification 动态查询（tags 由全局 batch_fetch 批量化）。</p>
     */
    @Transactional(readOnly = true)
    public String exportTransactionsToCSV(Long ledgerId, TransactionType type, LocalDate startDate, LocalDate endDate, List<Long> tagIds, String createdBy, Long categoryId, Long accountId, String merchant, BigDecimal minAmount, BigDecimal maxAmount) {
        logger.debug("导出账本 {} 的交易记录，筛选条件 - 类型: {}, 日期范围: {} - {}, 标签: {}, 创建人: {}, 分类: {}, 账户: {}, 商家: {}, 金额: {} - {}",
                ledgerId, type, startDate, endDate, tagIds, createdBy, categoryId, accountId, merchant, minAmount, maxAmount);

        validateQueryFilters(ledgerId, accountId, minAmount, maxAmount);
        if (type == null && startDate == null && endDate == null && (tagIds == null || tagIds.isEmpty())
                && (createdBy == null || createdBy.isBlank()) && categoryId == null
                && accountId == null && (merchant == null || merchant.isBlank())
                && minAmount == null && maxAmount == null) {
            return exportTransactionsToCSV(ledgerId);
        }

        List<Transaction> transactions = queryTransactionsForExport(
                ledgerId, type, startDate, endDate, tagIds, createdBy, categoryId,
                accountId, merchant, minAmount, maxAmount);
        return buildCSV(transactions, accountService.getAccountNameMap(ledgerId));
    }

    /**
     * 根据筛选条件查询交易记录（不分页，用于导出）
     * 使用 JPA Specification 实现动态查询
     */
    private List<Transaction> queryTransactionsForExport(Long ledgerId, TransactionType type, LocalDate startDate, LocalDate endDate, List<Long> tagIds, String createdBy, Long categoryId, Long accountId, String merchant, BigDecimal minAmount, BigDecimal maxAmount) {
        Specification<Transaction> spec = TransactionSpecifications.buildQuery(
                ledgerId, null, type, startDate, endDate, tagIds, createdBy, categoryId,
                accountId, merchant, minAmount, maxAmount);

        // 按 ID 降序排序
        Sort sort = Sort.by(Sort.Direction.DESC, "id");

        return transactionRepository.findAll(spec, sort);
    }

    private void validateQueryFilters(Long ledgerId, Long accountId, BigDecimal minAmount, BigDecimal maxAmount) {
        if (accountId != null && !accountRepository.existsByIdAndLedgerId(accountId, ledgerId)) {
            throw new BusinessException("账户不属于当前账本");
        }
        if (minAmount != null && maxAmount != null && minAmount.compareTo(maxAmount) > 0) {
            throw new BusinessException("最小金额不能大于最大金额");
        }
    }

    /**
     * 构建CSV内容（使用 Apache Commons CSV）
     */
    private String buildCSV(List<Transaction> transactions, Map<Long, String> accountNames) {
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        try (StringWriter writer = new StringWriter();
             CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT.builder()
                     .setHeader("ID", "交易日期", "类型", "金额", "描述", "原始文本", "分类", "父分类",
                               "商户", "备注", "相关人员", "置信度", "AI模型", "创建人", "创建时间", "更新时间", "标签", "账户", "转入账户")
                     .build())) {

            for (Transaction t : transactions) {
                // 标签列（逗号分隔）
                String tagsStr = "";
                if (t.getTags() != null && !t.getTags().isEmpty()) {
                    tagsStr = t.getTags().stream()
                            .map(Tag::getName)
                            .sorted()
                            .map(this::neutralizeCsvFormula)
                            .collect(Collectors.joining(","));
                }

                String accountName = t.getAccountId() == null ? "" : accountNames.getOrDefault(t.getAccountId(), "");
                String counterAccountName = t.getCounterAccountId() == null ? "" : accountNames.getOrDefault(t.getCounterAccountId(), "");

                csvPrinter.printRecord(
                        t.getId(),
                        t.getTransactionDate().format(dateFormatter),
                        t.getType().toString(),
                        t.getAmount().toString(),
                        neutralizeCsvFormula(t.getDescription()),
                        neutralizeCsvFormula(t.getOriginalText()),
                        t.getCategory() != null ? t.getCategory() : "",
                        t.getParentCategoryName() != null ? t.getParentCategoryName() : "",
                        t.getParsedMerchant() != null ? neutralizeCsvFormula(t.getParsedMerchant()) : "",
                        t.getNote() != null ? neutralizeCsvFormula(t.getNote()) : "",
                        t.getRelatedUser() != null ? neutralizeCsvFormula(t.getRelatedUser()) : "",
                        t.getConfidenceScore() != null ? t.getConfidenceScore().toString() : "",
                        t.getAiModelUsed() != null ? t.getAiModelUsed() : "",
                        t.getCreatedBy() != null ? t.getCreatedBy() : "",
                        t.getCreatedAt() != null ? t.getCreatedAt().format(dateTimeFormatter) : "",
                        t.getUpdatedAt() != null ? t.getUpdatedAt().format(dateTimeFormatter) : "",
                        tagsStr,
                        accountName,
                        counterAccountName
                );
            }

            csvPrinter.flush();
            return writer.toString();

        } catch (IOException e) {
            logger.error("生成CSV失败", e);
            throw new BusinessException("生成CSV失败: " + e.getMessage());
        }
    }

    /**
     * 中和 CSV 公式注入：用户可控文本以 =、+、-、@、Tab、CR 开头时加前导单引号，
     * 防止导出文件在 Excel/WPS 中被当公式执行（CSV Injection）。
     * 金额等数字列由代码格式化输出、不经过此处，保证「导出→再导入」可往返。
     */
    private String neutralizeCsvFormula(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        char first = value.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            return "'" + value;
        }
        return value;
    }

    /**
     * 更新现有交易记录
     */
    private void updateExistingTransaction(Transaction existing, UpdateTransactionRequest request, Category managedCategory) {
        existing.setAmount(request.getAmount());
        existing.setType(request.getType());
        existing.setDescription(request.getDescription());
        existing.setTransactionDate(request.getTransactionDate());
        existing.setParsedMerchant(request.getParsedMerchant());
        existing.setNote(request.getNote());
        existing.setRelatedUser(request.getRelatedUser());

        if (managedCategory != null) {
            applyCategory(existing, managedCategory);
        }
    }

    private Category requireRegularCategory(Long categoryId, TransactionType type) {
        if (type == null) {
            throw new BusinessException("交易类型不能为空");
        }
        if (type == TransactionType.TRANSFER) {
            throw new BusinessException("转账必须使用转账专用接口");
        }
        if (categoryId == null) {
            throw new BusinessException("分类不能为空");
        }

        Category category = categoryService.getCategoryEntityById(categoryId)
                .orElseThrow(() -> new BusinessException("分类不存在: " + categoryId));
        if (category.getType() != type) {
            throw new BusinessException("分类类型与交易类型不一致");
        }
        return category;
    }

    private void applyCategory(Transaction transaction, Category category) {
        transaction.setCategoryEntity(category);
    }

    private PageResponseDto<TransactionDto> toPageResponse(Page<Transaction> transactions) {
        List<TransactionDto> content = transactions.getContent().stream()
                .map(transactionMapper::toDto)
                .collect(Collectors.toList());
        return PageResponseDto.from(transactions, content);
    }

    /**
     * 校验并设置交易关联账户；accountId 为空表示不关联/解除关联。
     */
    private void applyAccount(Transaction transaction, Long accountId, Long ledgerId) {
        if (accountId == null) {
            transaction.setAccountId(null);
            return;
        }
        accountService.requireAccountInLedger(ledgerId, accountId);
        transaction.setAccountId(accountId);
    }

    /**
     * 更新交易的标签（限定当前账本；角色权限由拦截器保证为 EDITOR 及以上）
     */
    @Transactional
    public void updateTransactionTags(Long ledgerId, Long transactionId, List<Long> tagIds) {
        logger.debug("账本 {} 更新交易 {} 的标签", ledgerId, transactionId);

        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("交易记录不存在: " + transactionId));

        // 跨账本按不存在处理（404），不暴露其他账本资源的存在性
        if (!ledgerId.equals(transaction.getLedgerId())) {
            throw new ResourceNotFoundException("交易记录不存在: " + transactionId);
        }

        // 批量查询所有标签并校验归属（避免N+1查询）
        Set<Tag> tags = tagService.findAllByIdsAndLedger(tagIds, ledgerId);

        transaction.setTags(tags);
        transactionRepository.save(transaction);
        logger.info("账本 {} 更新交易 {} 的标签成功，共 {} 个标签", ledgerId, transactionId, tags.size());
    }

    /**
     * CSV 导入（使用 Apache Commons CSV）。整体保持单事务：任一行失败仅记入错误列表，
     * 成功行照常入库（与既有语义一致）。
     */
    @Transactional
    public ImportResult importTransactionsFromCSV(Long ledgerId, String username, MultipartFile file) {
        logger.info("用户 {} 向账本 {} 导入交易记录 CSV", username, ledgerId);

        ImportResult result = new ImportResult();
        List<ImportResult.ImportError> errors = new ArrayList<>();
        int created = 0, updated = 0;

        String content = readCsvContent(file);

        // 使用 Apache Commons CSV 解析
        try (CSVParser csvParser = CSVParser.parse(new StringReader(content),
                CSVFormat.DEFAULT.builder()
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .setIgnoreEmptyLines(true)
                        .setTrim(true)
                        .build())) {

            // 校验必需列
            List<String> requiredHeaders = Arrays.asList("ID", "交易日期", "类型", "金额", "描述", "原始文本", "创建人");
            for (String required : requiredHeaders) {
                if (!csvParser.getHeaderMap().containsKey(required)) {
                    throw new BusinessException("CSV 缺少必需列: " + required);
                }
            }

            // 预载分类/标签/账户映射，循环内查内存 Map，消除逐行查库
            CategoryService.CategoryNameIndex categoryIndex = categoryService.buildCategoryNameIndex();
            Map<String, Tag> tagCache = tagService.preloadTagMap(ledgerId);
            Map<String, Long> accountIdByName = new HashMap<>();
            accountService.getAccountNameMap(ledgerId).forEach((id, name) -> accountIdByName.put(name, id));

            int rowNum = 1; // 从1开始（跳过表头）
            for (CSVRecord record : csvParser) {
                rowNum++;
                try {
                    Transaction transaction = processCSVRecord(
                            ledgerId, username, record, rowNum, categoryIndex, tagCache, accountIdByName);

                    if (transaction.getId() == null) {
                        transactionRepository.save(transaction);
                        created++;
                    } else {
                        transactionRepository.save(transaction);
                        updated++;
                    }

                } catch (Exception e) {
                    errors.add(new ImportResult.ImportError(rowNum, "", e.getMessage()));
                    logger.warn("导入第 {} 行失败: {}", rowNum, e.getMessage());
                }
            }

            result.setTotalRows(rowNum - 1);
            result.setCreatedCount(created);
            result.setUpdatedCount(updated);
            result.setErrorCount(errors.size());
            result.setErrors(errors);

            logger.info("导入完成: 总 {} 行, 新增 {}, 更新 {}, 失败 {}",
                    result.getTotalRows(), created, updated, errors.size());

            return result;

        } catch (IOException e) {
            logger.error("解析CSV文件失败", e);
            throw new BusinessException("解析CSV文件失败: " + e.getMessage());
        }
    }

    /**
     * 读取并解码 CSV 文件内容：带 UTF-8 BOM 时按 UTF-8；否则先按 UTF-8 严格解码
     * （CodingErrorAction.REPORT），失败回退 GBK 严格解码。两者都失败宁可报错，
     * 也不把替换字符（乱码）静默写进数据库。
     */
    private String readCsvContent(MultipartFile file) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            logger.error("读取CSV文件失败", e);
            throw new BusinessException("读取CSV文件失败: " + e.getMessage());
        }

        boolean hasUtf8Bom = bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF;
        if (hasUtf8Bom) {
            return strictDecode(bytes, 3, StandardCharsets.UTF_8)
                    .orElseThrow(() -> new BusinessException("CSV 文件带 UTF-8 BOM 但内容不是有效的 UTF-8 编码"));
        }

        return strictDecode(bytes, 0, StandardCharsets.UTF_8)
                .or(() -> strictDecode(bytes, 0, Charset.forName("GBK")))
                .orElseThrow(() -> new BusinessException("无法识别 CSV 文件编码，请使用 UTF-8 或 GBK 编码保存后重试"));
    }

    /** 按指定字符集严格解码（非法字节直接判失败，不产生替换字符） */
    private Optional<String> strictDecode(byte[] bytes, int offset, Charset charset) {
        CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return Optional.of(decoder.decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset)).toString());
        } catch (CharacterCodingException e) {
            return Optional.empty();
        }
    }

    /**
     * 处理 CSV 记录（使用 CSVRecord）。分类/标签/账户均查预载映射，不逐行查库。
     */
    private Transaction processCSVRecord(Long ledgerId, String username, CSVRecord record, int rowNum,
                                         CategoryService.CategoryNameIndex categoryIndex,
                                         Map<String, Tag> tagCache,
                                         Map<String, Long> accountIdByName) {
        // 1. 提取 ID（决定是新增还是更新）
        String idStr = getCSVValue(record, "ID");
        Long id = null;
        Transaction transaction = null;

        if (idStr != null && !idStr.isEmpty()) {
            try {
                id = Long.parseLong(idStr);
                transaction = transactionRepository.findById(id).orElse(null);

                if (transaction == null) {
                    throw new BusinessException("ID " + id + " 不存在");
                }

                if (!ledgerId.equals(transaction.getLedgerId())) {
                    throw new BusinessException("无权限修改 ID " + id + " 的记录（属于其他账本）");
                }
            } catch (NumberFormatException e) {
                throw new BusinessException("ID 格式错误: " + idStr);
            }
        }

        // 2. 如果是新增，创建新对象（归属当前账本，记录录入人）
        if (transaction == null) {
            transaction = new Transaction();
            transaction.setLedgerId(ledgerId);
            transaction.setCreatedBy(username);
        }

        // 3. 解析必需字段
        String dateStr = getCSVValue(record, "交易日期");
        if (dateStr == null || dateStr.isEmpty()) {
            throw new BusinessException("交易日期不能为空");
        }
        LocalDate transactionDate = LocalDate.parse(dateStr.split(" ")[0]);
        transaction.setTransactionDate(transactionDate);

        String typeStr = getCSVValue(record, "类型");
        if (typeStr == null || typeStr.isEmpty()) {
            throw new BusinessException("类型不能为空");
        }
        TransactionType type = TransactionType.valueOf(typeStr);
        transaction.setType(type);

        String amountStr = getCSVValue(record, "金额");
        if (amountStr == null || amountStr.isEmpty()) {
            throw new BusinessException("金额不能为空");
        }
        BigDecimal amount = new BigDecimal(amountStr);
        transaction.setAmount(amount);

        String description = getCSVValue(record, "描述");
        if (description == null || description.isEmpty()) {
            throw new BusinessException("描述不能为空");
        }
        transaction.setDescription(description);

        String originalText = getCSVValue(record, "原始文本");
        if (originalText == null || originalText.isEmpty()) {
            throw new BusinessException("原始文本不能为空");
        }
        transaction.setOriginalText(originalText);

        // 4. 解析可选字段
        updateIfPresent(transaction::setNote, getCSVValue(record, "备注"));
        updateIfPresent(transaction::setRelatedUser, getCSVValue(record, "相关人员"));
        updateIfPresent(transaction::setParsedMerchant, getCSVValue(record, "商户"));

        // 5. 解析分类（转账行不需要业务分类，统一挂系统「转账」哨兵分类）
        if (type == TransactionType.TRANSFER) {
            transaction.setCategoryEntity(categoryService.getTransferCategory());
        } else {
            String categoryName = getCSVValue(record, "分类");
            String parentCategoryName = getCSVValue(record, "父分类");

            if (categoryName != null && !categoryName.isEmpty()) {
                Category category = categoryIndex.find(parentCategoryName, categoryName, type);
                if (category == null) {
                    logger.warn("第 {} 行: 未找到分类 {}/{}, 保留原分类", rowNum, parentCategoryName, categoryName);
                } else {
                    transaction.setCategoryEntity(category);
                }
            }

            if (transaction.getId() == null && transaction.getCategoryId() == null && transaction.getCategoryEntity() == null) {
                throw new BusinessException("新增记录必须提供分类");
            }

            // 类型一致校验（对齐 createTransaction/updateTransaction 不变量）：
            // 更新分支「保留原分类」时，行类型与既有分类类型不一致的行计为错误，不再静默落库
            Category effectiveCategory = transaction.getCategoryEntity();
            if (effectiveCategory != null && effectiveCategory.getType() != type) {
                throw new BusinessException("分类类型与交易类型不一致: 分类「" + effectiveCategory.getName()
                        + "」为 " + effectiveCategory.getType() + "，行类型为 " + type);
            }
        }

        // 6. 解析标签
        String tagsStr = getCSVValue(record, "标签");
        if (tagsStr != null) {
            if (tagsStr.isEmpty()) {
                transaction.setTags(new HashSet<>());
            } else {
                Set<Tag> tags = tagService.parseTags(ledgerId, username, tagsStr, tagCache);
                transaction.setTags(tags);
            }
        }

        // 7. 解析账户（可选）：有值则按名关联，账户不存在时自动建为 OTHER 类型
        String accountName = getCSVValue(record, "账户");
        if (accountName != null) {
            transaction.setAccountId(resolveAccountId(ledgerId, username, accountName, accountIdByName));
        }

        // 8. 转账行解析转入账户（counter_account_id）
        if (type == TransactionType.TRANSFER) {
            String counterAccountName = getCSVValue(record, "转入账户");
            if (counterAccountName != null) {
                transaction.setCounterAccountId(resolveAccountId(ledgerId, username, counterAccountName, accountIdByName));
            }
        } else {
            transaction.setCounterAccountId(null);
        }

        return transaction;
    }

    /**
     * 按名称解析账户 ID：先查预载映射，未命中才建新账户并回填映射复用。
     */
    private Long resolveAccountId(Long ledgerId, String username, String accountName, Map<String, Long> accountIdByName) {
        Long accountId = accountIdByName.get(accountName);
        if (accountId == null) {
            Account account = accountService.getOrCreateAccount(ledgerId, username, accountName);
            accountId = account.getId();
            accountIdByName.put(accountName, accountId);
        }
        return accountId;
    }

    /**
     * 从 CSVRecord 安全获取值
     */
    private String getCSVValue(CSVRecord record, String columnName) {
        if (!record.isMapped(columnName)) {
            return null;
        }
        String value = record.get(columnName);
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    /**
     * 有值才更新
     */
    private void updateIfPresent(Consumer<String> setter, String value) {
        if (value != null && !value.isEmpty()) {
            setter.accept(value);
        }
    }

    /**
     * AI 解析（含降级回退到规则解析）。
     * AI 成功时返回的 result 含 categoryId；规则降级时 categoryId 为 null，由调用方补齐。
     */
    private ParseAttempt doAIParseWithFallback(String text) {
        String treeText = categoryService.getFullCategoryTreeText();
        try {
            return new ParseAttempt(aiParserService.parseTransactionAndCategory(text, treeText), false);
        } catch (Exception e) {
            logger.warn("合并 AI 解析失败，回退到规则解析: {}", e.getMessage());
            return new ParseAttempt(
                    new TransactionAndCategoryParseResult(aiParserService.parseWithSimpleRules(text)),
                    true
            );
        }
    }
}
