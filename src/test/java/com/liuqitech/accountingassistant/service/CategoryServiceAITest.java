package com.liuqitech.accountingassistant.service;

import com.liuqitech.accountingassistant.dto.CategoryDto;
import com.liuqitech.accountingassistant.dto.CategoryParseResult;
import com.liuqitech.accountingassistant.entity.Category;
import com.liuqitech.accountingassistant.enums.TransactionType;
import com.liuqitech.accountingassistant.repository.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CategoryService AI功能测试
 */
@ExtendWith(MockitoExtension.class)
class CategoryServiceAITest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private AIParserService aiParserService;

    @InjectMocks
    private CategoryService categoryService;

    private Category foodCategory;
    private Category breakfastCategory;
    private Category shoppingCategory;
    private Category incomeCategory;

    @BeforeEach
    void setUp() {
        // 设置测试数据
        foodCategory = new Category();
        foodCategory.setId(1L);
        foodCategory.setName("食品酒水");
        foodCategory.setLevel(1);
        foodCategory.setType(TransactionType.EXPENSE);
        foodCategory.setDescription("食品酒水相关支出");

        breakfastCategory = new Category();
        breakfastCategory.setId(2L);
        breakfastCategory.setName("早餐");
        breakfastCategory.setLevel(2);
        breakfastCategory.setType(TransactionType.EXPENSE);
        breakfastCategory.setDescription("早餐支出");
        breakfastCategory.setParent(foodCategory);

        shoppingCategory = new Category();
        shoppingCategory.setId(3L);
        shoppingCategory.setName("购物消费");
        shoppingCategory.setLevel(1);
        shoppingCategory.setType(TransactionType.EXPENSE);
        shoppingCategory.setDescription("购物消费相关支出");

        incomeCategory = new Category();
        incomeCategory.setId(4L);
        incomeCategory.setName("工资收入");
        incomeCategory.setLevel(1);
        incomeCategory.setType(TransactionType.INCOME);
        incomeCategory.setDescription("工资收入");
    }

    @Test
    void testFindBestMatchCategory_WithAI() {
        // 准备测试数据
        String description = "今天早上在星巴克买了咖啡和面包";
        TransactionType type = TransactionType.EXPENSE;
        
        // 模拟AI结构化解析结果
        CategoryParseResult aiResult = buildParseResult(2L, "早餐", null, 0.95, "星巴克咖啡和面包属于早餐类别");

        // 模拟Repository返回
        when(categoryRepository.findByType(type)).thenReturn(Arrays.asList(foodCategory, shoppingCategory));
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(breakfastCategory));
        when(aiParserService.parseStructured(anyString(), eq(CategoryParseResult.class))).thenReturn(aiResult);

        // 执行测试
        Optional<Category> result = categoryService.findBestMatchCategory(description, type);

        // 验证结果
        assertTrue(result.isPresent());
        assertEquals(2L, result.get().getId());
        assertEquals("早餐", result.get().getName());

        // 验证调用
        verify(aiParserService).parseStructured(anyString(), eq(CategoryParseResult.class));
        verify(categoryRepository).findByType(type);
        verify(categoryRepository).findById(2L);
    }

    @Test
    void testFindBestMatchCategory_WithAIRevertToRules() {
        // 准备测试数据
        String description = "超市买菜";
        TransactionType type = TransactionType.EXPENSE;
        
        // 模拟AI解析失败
        when(aiParserService.parseStructured(anyString(), eq(CategoryParseResult.class))).thenThrow(new RuntimeException("AI服务不可用"));
        when(categoryRepository.findByTypeAndLevel(type, 2)).thenReturn(Arrays.asList(breakfastCategory));
        when(categoryRepository.findByTypeAndLevel(type, 1)).thenReturn(Arrays.asList(foodCategory, shoppingCategory));

        // 执行测试
        Optional<Category> result = categoryService.findBestMatchCategory(description, type);

        // 验证结果 - 应该回退到规则匹配
        assertTrue(result.isPresent());

        // 验证调用
        verify(aiParserService).parseStructured(anyString(), eq(CategoryParseResult.class));
        verify(categoryRepository).findByTypeAndLevel(type, 2);
        verify(categoryRepository).findByTypeAndLevel(type, 1);
    }

    @Test
    void testFindBestMatchCategory_IgnoresCategoryWithMismatchedType() {
        String description = "早餐包子";
        TransactionType type = TransactionType.EXPENSE;
        CategoryParseResult aiResult = buildParseResult(
                incomeCategory.getId(), incomeCategory.getName(), null, 0.95, "错误的收入分类");

        when(categoryRepository.findByType(type)).thenReturn(List.of(foodCategory));
        when(categoryRepository.findById(incomeCategory.getId())).thenReturn(Optional.of(incomeCategory));
        when(aiParserService.parseStructured(anyString(), eq(CategoryParseResult.class))).thenReturn(aiResult);
        when(categoryRepository.findByTypeAndLevel(type, 2)).thenReturn(List.of(breakfastCategory));

        Optional<Category> result = categoryService.findBestMatchCategory(description, type);

        assertTrue(result.isPresent());
        assertEquals(breakfastCategory.getId(), result.get().getId());
    }

    @Test
    void testFindCategoryWithAIAndDetails() {
        // 准备测试数据
        String description = "在星巴克买咖啡";
        TransactionType type = TransactionType.EXPENSE;
        
        // 模拟AI结构化解析结果
        CategoryParseResult aiResult = buildParseResult(2L, "早餐", null, 0.9, "星巴克咖啡属于早餐类别");

        // 模拟Repository返回
        when(categoryRepository.findByType(type)).thenReturn(Arrays.asList(foodCategory, shoppingCategory));
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(breakfastCategory));
        when(aiParserService.parseStructured(anyString(), eq(CategoryParseResult.class))).thenReturn(aiResult);

        // 执行测试
        Optional<CategoryDto> result = categoryService.findCategoryWithAIAndDetails(description, type);

        // 验证结果
        assertTrue(result.isPresent());
        CategoryDto dto = result.get();
        assertEquals(2L, dto.getId());
        assertEquals("早餐", dto.getName());
        assertEquals(0.9, dto.getConfidence());
        assertEquals("星巴克咖啡属于早餐类别", dto.getReason());

        // 验证调用
        verify(aiParserService).parseStructured(anyString(), eq(CategoryParseResult.class));
        verify(categoryRepository).findByType(type);
        verify(categoryRepository).findById(2L);
    }

    /** 构造 AI 分类解析结果的测试辅助方法 */
    private CategoryParseResult buildParseResult(Long categoryId, String categoryName,
                                                 Long parentCategoryId, Double confidence, String reason) {
        CategoryParseResult result = new CategoryParseResult();
        result.setCategoryId(categoryId);
        result.setCategoryName(categoryName);
        result.setParentCategoryId(parentCategoryId);
        result.setConfidence(confidence);
        result.setReason(reason);
        return result;
    }

    @Test
    void testConvertToDto() {
        // 执行测试
        CategoryDto dto = categoryService.convertToDto(breakfastCategory);
        
        // 验证结果
        assertEquals(2L, dto.getId());
        assertEquals("早餐", dto.getName());
        assertEquals(2, dto.getLevel());
        assertEquals(TransactionType.EXPENSE, dto.getType());
        assertEquals("早餐支出", dto.getDescription());
        assertEquals(1L, dto.getParentId());
        assertEquals("食品酒水", dto.getParentName());
    }
}
