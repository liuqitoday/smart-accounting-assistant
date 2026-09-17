package com.liuqitech.accountingassistant.controller;

import com.liuqitech.accountingassistant.dto.ApiResponse;
import com.liuqitech.accountingassistant.dto.CategoryDto;
import com.liuqitech.accountingassistant.enums.TransactionType;
import com.liuqitech.accountingassistant.exception.ResourceNotFoundException;
import com.liuqitech.accountingassistant.service.CategoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 分类管理控制器
 */
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private static final Logger logger = LoggerFactory.getLogger(CategoryController.class);

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /**
     * 获取所有分类（树形结构）
     */
    @GetMapping
    public ApiResponse<List<CategoryDto>> getAllCategories(
            @RequestParam(required = false) TransactionType type) {

        logger.debug("获取分类列表: type={}", type);

        List<CategoryDto> categories;
        if (type != null) {
            categories = categoryService.getCategoriesByType(type);
        } else {
            categories = categoryService.getAllCategories();
        }

        return ApiResponse.success(categories);
    }

    /**
     * 根据ID获取分类
     */
    @GetMapping("/{id}")
    public ApiResponse<CategoryDto> getCategoryById(@PathVariable Long id) {
        logger.debug("根据ID获取分类: {}", id);

        CategoryDto category = categoryService.getCategoryById(id)
                .orElseThrow(() -> new ResourceNotFoundException("分类不存在: " + id));
        return ApiResponse.success(category);
    }

//    /**
//     * 创建分类
//     */
//    @PostMapping
//    public ResponseEntity<ApiResponse<CategoryDto>> createCategory(
//            @Valid @RequestBody CategoryDto categoryDto) {
//
//        logger.info("创建分类: {}", categoryDto);
//        CategoryDto createdCategory = categoryService.createCategory(categoryDto);
//        return ResponseEntity.ok(ApiResponse.success(createdCategory, "分类创建成功"));
//    }
//
//    /**
//     * 更新分类
//     */
//    @PutMapping("/{id}")
//    public ResponseEntity<ApiResponse<CategoryDto>> updateCategory(
//            @PathVariable Long id,
//            @Valid @RequestBody CategoryDto categoryDto) {
//
//        logger.info("更新分类: {} -> {}", id, categoryDto);
//        Optional<CategoryDto> updatedCategory = categoryService.updateCategory(id, categoryDto);
//
//        if (updatedCategory.isPresent()) {
//            return ResponseEntity.ok(ApiResponse.success(updatedCategory.get(), "分类更新成功"));
//        } else {
//            return ResponseEntity.notFound().build();
//        }
//    }
//
//    /**
//     * 删除分类
//     */
//    @DeleteMapping("/{id}")
//    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable Long id) {
//        logger.info("删除分类: {}", id);
//        boolean deleted = categoryService.deleteCategory(id);
//
//        if (deleted) {
//            return ResponseEntity.ok(ApiResponse.success(null, "分类删除成功"));
//        } else {
//            return ResponseEntity.notFound().build();
//        }
//    }

    // 原 /ai-parse、/smart-match 端点已删除：前端无引用，且未限流的 AI 调用入口
    // 属于滥用暴露面（每次调用都消耗 AI 配额）。AI 分类能力仍经 /api/transactions/parse* 提供。
}
