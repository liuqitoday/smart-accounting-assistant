package com.liuqitech.accountingassistant.service;

import com.liuqitech.accountingassistant.dto.CreateRecurringBillRequest;
import com.liuqitech.accountingassistant.dto.GenerateRecurringBillsResult;
import com.liuqitech.accountingassistant.dto.RecurringBillDto;
import com.liuqitech.accountingassistant.dto.UpdateRecurringBillRequest;
import com.liuqitech.accountingassistant.entity.Category;
import com.liuqitech.accountingassistant.entity.RecurringBill;
import com.liuqitech.accountingassistant.entity.Tag;
import com.liuqitech.accountingassistant.enums.RecurringFrequency;
import com.liuqitech.accountingassistant.enums.TransactionType;
import com.liuqitech.accountingassistant.exception.BusinessException;
import com.liuqitech.accountingassistant.exception.ResourceNotFoundException;
import com.liuqitech.accountingassistant.repository.RecurringBillRepository;
import com.liuqitech.accountingassistant.util.AppClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RecurringBillService {

    private static final Logger logger = LoggerFactory.getLogger(RecurringBillService.class);

    private final RecurringBillRepository recurringBillRepository;
    private final CategoryService categoryService;
    private final AccountService accountService;
    private final TagService tagService;
    private final RecurringBillOccurrenceGenerator occurrenceGenerator;

    public RecurringBillService(RecurringBillRepository recurringBillRepository,
                                CategoryService categoryService,
                                AccountService accountService,
                                TagService tagService,
                                RecurringBillOccurrenceGenerator occurrenceGenerator) {
        this.recurringBillRepository = recurringBillRepository;
        this.categoryService = categoryService;
        this.accountService = accountService;
        this.tagService = tagService;
        this.occurrenceGenerator = occurrenceGenerator;
    }

    @Transactional(readOnly = true)
    public List<RecurringBillDto> list(Long ledgerId) {
        return recurringBillRepository.findByLedgerIdAndDeletedAtIsNullOrderByIdDesc(ledgerId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public RecurringBillDto create(Long ledgerId, String username, CreateRecurringBillRequest request) {
        RecurringBill bill = new RecurringBill();
        bill.setLedgerId(ledgerId);
        bill.setCreatedBy(username);
        applyRequest(bill, ledgerId, request, true);
        bill.setEnabled(request.getEnabled() == null || request.getEnabled());

        RecurringBill saved = recurringBillRepository.save(bill);
        logger.info("账本 {} 创建周期账单规则成功: {}", ledgerId, saved.getName());
        return toDto(saved);
    }

    @Transactional
    public RecurringBillDto update(Long ledgerId, Long id, UpdateRecurringBillRequest request) {
        RecurringBill bill = requireBill(ledgerId, id);
        applyRequest(bill, ledgerId, request, false);
        if (request.getEnabled() != null) {
            bill.setEnabled(request.getEnabled());
        }

        RecurringBill saved = recurringBillRepository.save(bill);
        logger.info("账本 {} 更新周期账单规则成功: {}", ledgerId, saved.getName());
        return toDto(saved);
    }

    @Transactional
    public RecurringBillDto setEnabled(Long ledgerId, Long id, boolean enabled) {
        RecurringBill bill = requireBill(ledgerId, id);
        if (enabled && !bill.isEnabled()) {
            fastForwardForReenable(bill);
        }
        bill.setEnabled(enabled);
        RecurringBill saved = recurringBillRepository.save(bill);
        logger.info("账本 {} {}周期账单规则: {}", ledgerId, enabled ? "启用" : "停用", saved.getName());
        return toDto(saved);
    }

    @Transactional
    public void delete(Long ledgerId, Long id) {
        RecurringBill bill = requireBill(ledgerId, id);
        bill.setEnabled(false);
        bill.setDeletedAt(AppClock.now());
        recurringBillRepository.save(bill);
        logger.info("账本 {} 删除周期账单规则: {}", ledgerId, bill.getName());
    }

    public GenerateRecurringBillsResult generateDueForLedger(Long ledgerId) {
        LocalDate today = AppClock.today();
        List<RecurringBill> bills = recurringBillRepository
                .findByLedgerIdAndEnabledTrueAndDeletedAtIsNullAndNextRunDateLessThanEqual(ledgerId, today);
        return generateDue(bills, today);
    }

    public GenerateRecurringBillsResult generateDueForBill(Long ledgerId, Long id) {
        RecurringBill bill = requireBill(ledgerId, id);
        return generateDue(List.of(bill), AppClock.today());
    }

    public GenerateRecurringBillsResult generateDueForAllLedgers() {
        LocalDate today = AppClock.today();
        List<RecurringBill> bills = recurringBillRepository
                .findByEnabledTrueAndDeletedAtIsNullAndNextRunDateLessThanEqual(today);
        return generateDue(bills, today);
    }

    private GenerateRecurringBillsResult generateDue(List<RecurringBill> bills, LocalDate today) {
        GenerateRecurringBillsResult total = new GenerateRecurringBillsResult();
        for (RecurringBill bill : bills) {
            try {
                total.merge(occurrenceGenerator.generateForRule(bill, today));
            } catch (RuntimeException e) {
                logger.error("账本 {} 周期账单规则 {} 生成失败，已跳过: {}",
                        bill.getLedgerId(), bill.getId(), e.getMessage(), e);
                total.addProcessedRule();
            }
        }
        return total;
    }

    private void applyRequest(RecurringBill bill, Long ledgerId, CreateRecurringBillRequest request, boolean create) {
        validateRequest(request);

        Long effectiveCategoryId;
        Long counterAccountId;
        if (request.getType() == TransactionType.TRANSFER) {
            if (request.getAccountId() == null || request.getCounterAccountId() == null) {
                throw new BusinessException("请选择转出和转入账户");
            }
            if (request.getAccountId().equals(request.getCounterAccountId())) {
                throw new BusinessException("转出和转入账户不能相同");
            }
            accountService.requireAccountInLedger(ledgerId, request.getAccountId());
            accountService.requireAccountInLedger(ledgerId, request.getCounterAccountId());
            effectiveCategoryId = categoryService.getTransferCategory().getId();
            counterAccountId = request.getCounterAccountId();
        } else {
            if (request.getCategoryId() == null) {
                throw new BusinessException("分类不能为空");
            }
            Category category = categoryService.getCategoryEntityById(request.getCategoryId())
                    .orElseThrow(() -> new BusinessException("分类不存在: " + request.getCategoryId()));
            if (request.getType() != category.getType()) {
                throw new BusinessException("分类类型与交易类型不一致");
            }
            if (request.getAccountId() != null) {
                accountService.requireAccountInLedger(ledgerId, request.getAccountId());
            }
            effectiveCategoryId = request.getCategoryId();
            counterAccountId = null;
        }

        LocalDate anchorDate = create ? request.getStartDate() : max(request.getStartDate(), AppClock.today());
        LocalDate nextRunDate = RecurringBillScheduleCalculator.firstRunOnOrAfter(
                request.getFrequency(), anchorDate, request.getDayOfMonth());
        if (request.getEndDate() != null && nextRunDate.isAfter(request.getEndDate())) {
            throw new BusinessException("结束日期早于首次生成日");
        }

        Set<Tag> tags = tagService.findAllByIdsAndLedger(request.getTagIds(), ledgerId);

        bill.setName(request.getName().trim());
        bill.setAmount(request.getAmount());
        bill.setType(request.getType());
        bill.setDescription(request.getDescription().trim());
        bill.setCategoryId(effectiveCategoryId);
        bill.setAccountId(request.getAccountId());
        bill.setCounterAccountId(counterAccountId);
        bill.setParsedMerchant(blankToNull(request.getParsedMerchant()));
        bill.setRelatedUser(blankToNull(request.getRelatedUser()));
        bill.setNote(blankToNull(request.getNote()));
        bill.setFrequency(request.getFrequency());
        bill.setDayOfMonth(request.getDayOfMonth());
        bill.setStartDate(request.getStartDate());
        bill.setEndDate(request.getEndDate());
        bill.setNextRunDate(nextRunDate);
        bill.setTags(tags);
    }

    private void validateRequest(CreateRecurringBillRequest request) {
        if (request.getFrequency() != RecurringFrequency.MONTHLY) {
            throw new BusinessException("当前仅支持按月重复");
        }
        if (request.getEndDate() != null && request.getEndDate().isBefore(request.getStartDate())) {
            throw new BusinessException("结束日期不能早于开始日期");
        }
    }

    private RecurringBill requireBill(Long ledgerId, Long id) {
        return recurringBillRepository.findByIdAndLedgerIdAndDeletedAtIsNull(id, ledgerId)
                .orElseThrow(() -> new ResourceNotFoundException("周期账单规则不存在: " + id));
    }

    private void fastForwardForReenable(RecurringBill bill) {
        LocalDate today = AppClock.today();
        if (bill.getEndDate() != null && bill.getEndDate().isBefore(today)) {
            throw new BusinessException("周期账单已结束，无法重新启用");
        }

        LocalDate anchorDate = max(bill.getStartDate(), today);
        LocalDate nextRunDate = RecurringBillScheduleCalculator.firstRunOnOrAfter(
                bill.getFrequency(), anchorDate, bill.getDayOfMonth());
        if (bill.getEndDate() != null && nextRunDate.isAfter(bill.getEndDate())) {
            throw new BusinessException("周期账单已结束，无法重新启用");
        }
        bill.setNextRunDate(nextRunDate);
    }

    private RecurringBillDto toDto(RecurringBill bill) {
        RecurringBillDto dto = new RecurringBillDto();
        dto.setId(bill.getId());
        dto.setName(bill.getName());
        dto.setEnabled(bill.isEnabled());
        dto.setAmount(bill.getAmount());
        dto.setType(bill.getType());
        dto.setDescription(bill.getDescription());
        dto.setCategoryId(bill.getCategoryId());
        dto.setAccountId(bill.getAccountId());
        dto.setCounterAccountId(bill.getCounterAccountId());
        dto.setParsedMerchant(bill.getParsedMerchant());
        dto.setRelatedUser(bill.getRelatedUser());
        dto.setNote(bill.getNote());
        dto.setFrequency(bill.getFrequency());
        dto.setDayOfMonth(bill.getDayOfMonth());
        dto.setStartDate(bill.getStartDate());
        dto.setEndDate(bill.getEndDate());
        dto.setNextRunDate(bill.getNextRunDate());
        dto.setCreatedBy(bill.getCreatedBy());
        dto.setCreatedAt(bill.getCreatedAt());
        dto.setUpdatedAt(bill.getUpdatedAt());
        dto.setTags(tagService.convertToDtoList(bill.getTags()));
        return dto;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }
}
