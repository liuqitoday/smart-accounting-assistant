package com.liuqitech.accountingassistant.service;

import com.liuqitech.accountingassistant.dto.TransactionAndCategoryParseResult;
import com.liuqitech.accountingassistant.dto.TransactionParseRequest;
import com.liuqitech.accountingassistant.dto.TransactionParseResponse;
import com.liuqitech.accountingassistant.dto.TransactionParseResult;
import com.liuqitech.accountingassistant.dto.UpdateTransactionRequest;
import com.liuqitech.accountingassistant.entity.Category;
import com.liuqitech.accountingassistant.entity.Tag;
import com.liuqitech.accountingassistant.entity.Transaction;
import com.liuqitech.accountingassistant.enums.TransactionType;
import com.liuqitech.accountingassistant.exception.BusinessException;
import com.liuqitech.accountingassistant.mapper.TransactionMapper;
import com.liuqitech.accountingassistant.repository.AccountRepository;
import com.liuqitech.accountingassistant.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceValidationTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private AIParserService aiParserService;
    @Mock
    private CategoryService categoryService;
    @Mock
    private CategoryCorrectionService categoryCorrectionService;
    @Mock
    private TagService tagService;
    @Mock
    private AccountService accountService;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private TransactionMapper transactionMapper;
    @Mock
    private PlatformTransactionManager transactionManager;

    private TransactionService transactionService;

    @BeforeEach
    void setUp() {
        transactionService = new TransactionService(
                transactionRepository,
                aiParserService,
                categoryService,
                categoryCorrectionService,
                tagService,
                accountService,
                accountRepository,
                transactionMapper,
                new TransactionTemplate(transactionManager),
                "test-model"
        );
    }

    @Test
    void createRegularTransactionRejectsTransferType() {
        TransactionCreationCommand command = command(TransactionType.TRANSFER, 1L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> transactionService.createTransaction(command)
        );

        assertEquals("转账必须使用转账专用接口", exception.getMessage());
        verify(transactionRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createRegularTransactionRejectsMismatchedCategoryType() {
        Category expenseCategory = category(1L, TransactionType.EXPENSE);
        when(categoryService.getCategoryEntityById(1L)).thenReturn(Optional.of(expenseCategory));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> transactionService.createTransaction(command(TransactionType.INCOME, 1L))
        );

        assertEquals("分类类型与交易类型不一致", exception.getMessage());
        verify(transactionRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updateRejectsExistingTransfer() {
        Transaction transfer = transaction(10L, TransactionType.TRANSFER);
        when(transactionRepository.findById(10L)).thenReturn(Optional.of(transfer));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> transactionService.updateTransaction(
                        10L, updateRequest(TransactionType.EXPENSE, 1L), 20L)
        );

        assertEquals("转账不能通过普通交易接口修改，请使用转账专用功能", exception.getMessage());
        verify(transactionRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updateRejectsConversionToTransfer() {
        Transaction expense = transaction(10L, TransactionType.EXPENSE);
        when(transactionRepository.findById(10L)).thenReturn(Optional.of(expense));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> transactionService.updateTransaction(
                        10L, updateRequest(TransactionType.TRANSFER, 1L), 20L)
        );

        assertEquals("转账必须使用转账专用接口", exception.getMessage());
        verify(transactionRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updateRejectsMismatchedCategoryType() {
        Transaction expense = transaction(10L, TransactionType.EXPENSE);
        Category incomeCategory = category(2L, TransactionType.INCOME);
        when(transactionRepository.findById(10L)).thenReturn(Optional.of(expense));
        when(categoryService.getCategoryEntityById(2L)).thenReturn(Optional.of(incomeCategory));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> transactionService.updateTransaction(
                        10L, updateRequest(TransactionType.EXPENSE, 2L), 20L)
        );

        assertEquals("分类类型与交易类型不一致", exception.getMessage());
        verify(transactionRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void parseOnlyReportsRuleFallbackAndDoesNotClaimAiModel() {
        String text = "午餐 35 元";
        TransactionParseResult fallbackResult = new TransactionParseResult(
                new BigDecimal("35.00"),
                TransactionType.EXPENSE,
                "未知商家",
                text,
                LocalDate.of(2026, 8, 4),
                null,
                null,
                new BigDecimal("0.60")
        );
        Category category = category(1L, TransactionType.EXPENSE);

        when(categoryService.getFullCategoryTreeText()).thenReturn("category tree");
        when(aiParserService.parseTransactionAndCategory(text, "category tree"))
                .thenThrow(new RuntimeException("AI unavailable"));
        when(aiParserService.parseWithSimpleRules(text)).thenReturn(fallbackResult);
        when(categoryCorrectionService.findCorrectedCategory(20L, TransactionType.EXPENSE, "未知商家", text))
                .thenReturn(Optional.empty());
        when(categoryService.findCategoryWithRules(text, TransactionType.EXPENSE))
                .thenReturn(Optional.of(category));

        TransactionParseResponse response = transactionService.parseTransactionOnly(
                new TransactionParseRequest(text), 20L);

        assertTrue(response.isFallbackUsed());
        assertNull(response.getAiModelUsed());
        assertEquals(new BigDecimal("35.00"), response.getAmount());
        assertEquals(1L, response.getCategoryId());
    }

    @Test
    void parseOnlyReportsAiResultWithoutFallback() {
        String text = "星巴克咖啡 35 元";
        TransactionAndCategoryParseResult aiResult = new TransactionAndCategoryParseResult();
        aiResult.setAmount(new BigDecimal("35.00"));
        aiResult.setType(TransactionType.EXPENSE);
        aiResult.setMerchant("星巴克");
        aiResult.setDescription("购买咖啡");
        aiResult.setTransactionDate(LocalDate.of(2026, 8, 4));
        aiResult.setConfidence(new BigDecimal("0.95"));
        aiResult.setCategoryId(1L);
        Category category = category(1L, TransactionType.EXPENSE);

        when(categoryService.getFullCategoryTreeText()).thenReturn("category tree");
        when(aiParserService.parseTransactionAndCategory(text, "category tree")).thenReturn(aiResult);
        when(categoryCorrectionService.findCorrectedCategory(
                20L, TransactionType.EXPENSE, "星巴克", "购买咖啡"))
                .thenReturn(Optional.empty());
        when(categoryService.resolveAndValidateCategory(1L, null, TransactionType.EXPENSE))
                .thenReturn(Optional.of(category));

        TransactionParseResponse response = transactionService.parseTransactionOnly(
                new TransactionParseRequest(text), 20L);

        assertFalse(response.isFallbackUsed());
        assertEquals("test-model", response.getAiModelUsed());
    }

    @Test
    void exportNeutralizesFormulaPrefixesInUserTextFieldsButNotAmount() {
        Transaction t = new Transaction();
        t.setId(1L);
        t.setLedgerId(20L);
        t.setAmount(new BigDecimal("35.00"));
        t.setType(TransactionType.EXPENSE);
        t.setDescription("=1+1");
        t.setOriginalText("-2+3cmd");
        t.setTransactionDate(LocalDate.of(2026, 7, 10));
        t.setParsedMerchant("@SUM(A1)");
        t.setNote("+8613800000000");
        t.setRelatedUser("\t老王");
        Tag tag = new Tag();
        tag.setName("=HYPERLINK(\"http://evil\")");
        t.setTags(Set.of(tag));

        when(transactionRepository.findAllWithTagsByLedgerIdOrderByIdDesc(20L)).thenReturn(List.of(t));
        when(accountService.getAccountNameMap(20L)).thenReturn(Map.of());

        String csv = transactionService.exportTransactionsToCSV(20L);

        // 用户可控文本字段以 =、+、-、@、Tab 开头时被加前导单引号中和
        assertTrue(csv.contains("'=1+1"), "description 应被中和: " + csv);
        assertTrue(csv.contains("'-2+3cmd"), "originalText 应被中和: " + csv);
        assertTrue(csv.contains("'@SUM(A1)"), "parsedMerchant 应被中和: " + csv);
        assertTrue(csv.contains("'+8613800000000"), "note 应被中和: " + csv);
        assertTrue(csv.contains("'\t老王"), "relatedUser 应被中和: " + csv);
        assertTrue(csv.contains("'=HYPERLINK"), "标签名应被中和: " + csv);
        // 金额是数字格式化输出，不属于用户可控文本，不能加引号（否则破坏重新导入）
        assertTrue(csv.contains(",35.00,'=1+1"), "金额列应保持原样输出: " + csv);
        assertFalse(csv.contains("'35.00"), "金额列不应被加引号: " + csv);
    }

    private TransactionCreationCommand command(TransactionType type, Long categoryId) {
        TransactionCreationCommand command = new TransactionCreationCommand();
        command.setLedgerId(20L);
        command.setUsername("tester");
        command.setAmount(new BigDecimal("12.34"));
        command.setType(type);
        command.setDescription("测试交易");
        command.setOriginalText("测试交易");
        command.setCategoryId(categoryId);
        command.setTransactionDate(LocalDate.of(2026, 7, 10));
        return command;
    }

    private UpdateTransactionRequest updateRequest(TransactionType type, Long categoryId) {
        UpdateTransactionRequest request = new UpdateTransactionRequest();
        request.setAmount(new BigDecimal("12.34"));
        request.setType(type);
        request.setDescription("测试交易");
        request.setTransactionDate(LocalDate.of(2026, 7, 10));
        request.setCategoryId(categoryId);
        return request;
    }

    private Category category(Long id, TransactionType type) {
        Category category = new Category();
        category.setId(id);
        category.setName("测试分类");
        category.setLevel(1);
        category.setType(type);
        return category;
    }

    private Transaction transaction(Long id, TransactionType type) {
        Transaction transaction = new Transaction();
        transaction.setId(id);
        transaction.setLedgerId(20L);
        transaction.setType(type);
        return transaction;
    }
}
