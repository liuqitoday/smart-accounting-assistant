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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
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
        verify(aiParserService, times(1)).parseTransactionAndCategory(text, "category tree");
        verify(aiParserService, times(1)).parseWithSimpleRules(text);
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

    @ParameterizedTest
    @CsvSource({
            "NEEDS_CLARIFICATION,PARSE_NEEDS_CLARIFICATION",
            "MULTIPLE_TRANSACTIONS,PARSE_MULTIPLE_TRANSACTIONS",
            "TRANSFER,PARSE_TRANSFER",
            "UNSUPPORTED_CURRENCY,PARSE_UNSUPPORTED_CURRENCY",
            "NOT_A_TRANSACTION,PARSE_NOT_A_TRANSACTION",
            "unexpected-provider-status,PARSE_NEEDS_CLARIFICATION"
    })
    void explicitRejectionNeverFallsBackOrCreatesTransactions(String status, String errorCode) {
        TransactionAndCategoryParseResult result = new TransactionAndCategoryParseResult();
        result.setParseStatus(status);
        result.setReason("sensitive-provider-details");
        when(categoryService.getFullCategoryTreeText()).thenReturn("category tree");
        when(aiParserService.parseTransactionAndCategory(anyString(), anyString())).thenReturn(result);

        BusinessException preview = assertThrows(BusinessException.class,
                () -> transactionService.parseTransactionOnly(new TransactionParseRequest("转账500元或早餐5元、午饭20元"), 20L));
        BusinessException create = assertThrows(BusinessException.class,
                () -> transactionService.parseAndCreateTransaction(new TransactionParseRequest("转账500元或早餐5元、午饭20元"), 20L, "tester"));

        assertEquals(errorCode, preview.getErrorCode());
        assertEquals(errorCode, create.getErrorCode());
        assertFalse(preview.getMessage().contains("sensitive-provider"));
        assertFalse(preview.getMessage().contains("unexpected-provider-status"));
        verify(aiParserService, never()).parseWithSimpleRules(anyString());
        verify(categoryService, never()).findCategoryWithRules(any(), any());
        verifyNoInteractions(transactionRepository, transactionManager, categoryCorrectionService);
    }

    @ParameterizedTest
    @MethodSource("invalidModelResults")
    void incompleteOrInvalidModelResultFailsBeforeCategoryResolutionAndSave(TransactionAndCategoryParseResult result, String code) {
        when(categoryService.getFullCategoryTreeText()).thenReturn("category tree");
        when(aiParserService.parseTransactionAndCategory(anyString(), anyString())).thenReturn(result);

        BusinessException preview = assertThrows(BusinessException.class,
                () -> transactionService.parseTransactionOnly(new TransactionParseRequest("午餐28元"), 20L));
        BusinessException create = assertThrows(BusinessException.class,
                () -> transactionService.parseAndCreateTransaction(new TransactionParseRequest("午餐28元"), 20L, "tester"));

        assertEquals(code, preview.getErrorCode());
        assertEquals(code, create.getErrorCode());
        verify(aiParserService, never()).parseWithSimpleRules(anyString());
        verify(categoryService, never()).findCategoryWithRules(any(), any());
        verifyNoInteractions(transactionRepository, transactionManager, categoryCorrectionService);
    }

    private static Stream<Arguments> invalidModelResults() {
        Stream.Builder<Arguments> cases = Stream.builder();
        cases.add(Arguments.of(null, "PARSE_NEEDS_CLARIFICATION"));
        for (BigDecimal amount : new BigDecimal[] { null, BigDecimal.ZERO, new BigDecimal("-1"),
                new BigDecimal("1.001"), new BigDecimal("10000000000000") }) {
            TransactionAndCategoryParseResult result = validModelResult();
            result.setAmount(amount);
            cases.add(Arguments.of(result, "PARSE_INVALID_AMOUNT"));
        }
        for (LocalDate date : new LocalDate[] { null, LocalDate.of(0, 1, 1), LocalDate.of(10000, 1, 1) }) {
            TransactionAndCategoryParseResult result = validModelResult();
            result.setTransactionDate(date);
            cases.add(Arguments.of(result, "PARSE_INVALID_DATE"));
        }
        for (String description : new String[] { null, " \t\n" }) {
            TransactionAndCategoryParseResult result = validModelResult();
            result.setDescription(description);
            cases.add(Arguments.of(result, "PARSE_NEEDS_CLARIFICATION"));
        }
        TransactionAndCategoryParseResult missingType = validModelResult();
        missingType.setType(null);
        cases.add(Arguments.of(missingType, "PARSE_NEEDS_CLARIFICATION"));
        TransactionAndCategoryParseResult transfer = validModelResult();
        transfer.setType(TransactionType.TRANSFER);
        cases.add(Arguments.of(transfer, "PARSE_TRANSFER"));
        return cases.build();
    }

    @ParameterizedTest
    @CsvSource({"0.01,READY", "12.340,", "9999999999999.99,READY"})
    void readyResultsAcceptValidAmountsAndKeepLegacyStatusOptional(String value, String status) {
        TransactionAndCategoryParseResult result = validModelResult();
        result.setParseStatus(status);
        result.setAmount(new BigDecimal(value));
        result.setDescription("  午餐  ");
        when(categoryService.getFullCategoryTreeText()).thenReturn("category tree");
        when(aiParserService.parseTransactionAndCategory(anyString(), anyString())).thenReturn(result);
        when(categoryService.resolveAndValidateCategory(1L, null, TransactionType.EXPENSE))
                .thenReturn(Optional.of(category(1L, TransactionType.EXPENSE)));

        TransactionParseResponse response = transactionService.parseTransactionOnly(new TransactionParseRequest("午餐" + value + "元"), 20L);

        assertEquals(new BigDecimal(value), response.getAmount());
        assertEquals("午餐", response.getDescription());
        assertFalse(response.isFallbackUsed());
        verify(aiParserService, never()).parseWithSimpleRules(anyString());
    }

    @Test
    void businessExceptionFromParserIsPreservedWithoutFallback() {
        BusinessException rejected = new BusinessException("请补充金额", "PARSE_NEEDS_CLARIFICATION");
        when(categoryService.getFullCategoryTreeText()).thenReturn("category tree");
        when(aiParserService.parseTransactionAndCategory(anyString(), anyString())).thenThrow(rejected);

        assertSame(rejected, assertThrows(BusinessException.class,
                () -> transactionService.parseTransactionOnly(new TransactionParseRequest("早餐"), 20L)));
        verify(aiParserService, never()).parseWithSimpleRules(anyString());
        verifyNoInteractions(transactionRepository, categoryCorrectionService);
    }

    private static TransactionAndCategoryParseResult validModelResult() {
        TransactionAndCategoryParseResult result = new TransactionAndCategoryParseResult();
        result.setAmount(new BigDecimal("28.00"));
        result.setType(TransactionType.EXPENSE);
        result.setDescription("午餐");
        result.setTransactionDate(LocalDate.of(2026, 11, 10));
        result.setCategoryId(1L);
        return result;
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
