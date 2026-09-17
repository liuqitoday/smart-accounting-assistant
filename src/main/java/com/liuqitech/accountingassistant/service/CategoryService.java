package com.liuqitech.accountingassistant.service;

import com.liuqitech.accountingassistant.dto.CategoryDto;
import com.liuqitech.accountingassistant.dto.CategoryParseResult;
import com.liuqitech.accountingassistant.entity.Category;
import com.liuqitech.accountingassistant.enums.TransactionType;
import com.liuqitech.accountingassistant.exception.BusinessException;
import com.liuqitech.accountingassistant.repository.CategoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 分类服务类
 */
@Service
@Transactional
public class CategoryService {
    
    private static final Logger logger = LoggerFactory.getLogger(CategoryService.class);
    
    private final CategoryRepository categoryRepository;
    private final AIParserService aiParserService;
    
    public CategoryService(CategoryRepository categoryRepository, AIParserService aiParserService) {
        this.categoryRepository = categoryRepository;
        this.aiParserService = aiParserService;
    }
    
    /**
     * 获取所有分类（树形结构）
     */
    @Transactional(readOnly = true)
    public List<CategoryDto> getAllCategories() {
        logger.debug("获取所有分类");
        
        List<Category> topLevelCategories = categoryRepository.findByLevel(1);
        return topLevelCategories.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    /**
     * 根据类型获取分类
     */
    @Transactional(readOnly = true)
    public List<CategoryDto> getCategoriesByType(TransactionType type) {
        logger.debug("根据类型获取分类: {}", type);
        
        List<Category> topLevelCategories = categoryRepository.findTopLevelCategoriesByType(type);
        return topLevelCategories.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    /**
     * 根据ID获取分类
     */
    @Transactional(readOnly = true)
    public Optional<CategoryDto> getCategoryById(Long id) {
        logger.debug("根据ID获取分类: {}", id);

        return categoryRepository.findById(id)
                .map(this::convertToDto);
    }

    /**
     * 根据ID获取分类实体
     */
    @Transactional(readOnly = true)
    public Optional<Category> getCategoryEntityById(Long id) {
        return categoryRepository.findById(id);
    }

    /** 系统转账哨兵分类名称（type=TRANSFER，全局唯一，供转账行满足分类非空约束） */
    public static final String TRANSFER_CATEGORY_NAME = "转账";

    /**
     * 获取系统「转账」哨兵分类（由 TransferCategoryInitializer 在启动时初始化）。
     */
    @Transactional(readOnly = true)
    public Category getTransferCategory() {
        return categoryRepository.findByNameAndType(TRANSFER_CATEGORY_NAME, TransactionType.TRANSFER)
                .stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("系统「转账」分类未初始化"));
    }
    
    /**
     * 创建分类
     */
    @CacheEvict(cacheNames = "categoryTree", allEntries = true)
    public CategoryDto createCategory(CategoryDto categoryDto) {
        logger.debug("创建分类: {}", categoryDto);
        
        Category category = convertToEntity(categoryDto);
        Category savedCategory = categoryRepository.save(category);
        
        logger.info("分类创建成功: {}", savedCategory);
        return convertToDto(savedCategory);
    }
    
    /**
     * 更新分类
     */
    @CacheEvict(cacheNames = "categoryTree", allEntries = true)
    public Optional<CategoryDto> updateCategory(Long id, CategoryDto categoryDto) {
        logger.debug("更新分类: {} -> {}", id, categoryDto);
        
        return categoryRepository.findById(id)
                .map(existingCategory -> {
                    updateEntityFromDto(existingCategory, categoryDto);
                    Category savedCategory = categoryRepository.save(existingCategory);
                    logger.info("分类更新成功: {}", savedCategory);
                    return convertToDto(savedCategory);
                });
    }
    
    /**
     * 删除分类
     */
    @CacheEvict(cacheNames = "categoryTree", allEntries = true)
    public boolean deleteCategory(Long id) {
        logger.debug("删除分类: {}", id);
        
        if (categoryRepository.existsById(id)) {
            categoryRepository.deleteById(id);
            logger.info("分类删除成功: {}", id);
            return true;
        }
        return false;
    }
    
    /**
     * 智能匹配分类
     */
    @Transactional(readOnly = true)
    public Optional<Category> findBestMatchCategory(String description, TransactionType type) {
        return findBestMatchCategory(description, type, null);
    }

    /**
     * 智能匹配分类（带原始文本）
     */
    @Transactional(readOnly = true)
    public Optional<Category> findBestMatchCategory(String description, TransactionType type, String originalText) {
        logger.debug("智能匹配分类: {} ({}), 原始文本: {}", description, type, originalText);

        // 首先尝试AI分类解析
        try {
            Optional<Category> aiCategory = findCategoryWithAI(description, type, originalText);
            if (aiCategory.isPresent()) {
                logger.debug("AI解析分类成功: {}", aiCategory.get());
                return aiCategory;
            }
        } catch (Exception e) {
            logger.warn("AI分类解析失败，回退到规则匹配: {}", e.getMessage());
        }

        // 如果AI解析失败，使用传统规则匹配
        return findCategoryWithRules(description, type);
    }
    
    /**
     * 使用AI解析分类
     */
    private Optional<Category> findCategoryWithAI(String description, TransactionType type, String originalText) {
        logger.debug("使用AI解析分类: {} ({}), 原始文本: {}", description, type, originalText);

        try {
            // 构建包含所有分类信息的提示词
            String prompt = buildCategoryPrompt(description, type, originalText);

            // 调用AI服务，结构化解析为分类结果
            CategoryParseResult parseResult = aiParserService.parseStructured(prompt, CategoryParseResult.class);

            logger.debug("AI分类解析结果: {}", parseResult);

            if (parseResult != null && parseResult.getCategoryId() != null) {
                // 验证分类层级关系
                Optional<Category> category = validateAndGetCategory(parseResult, type);
                if (category.isPresent()) {
                    logger.info("AI分类解析成功: {} (置信度: {})", category.get().getName(), parseResult.getConfidence());
                    return category;
                } else {
                    logger.warn("AI返回的分类层级关系验证失败: {}", parseResult);
                }
            }

        } catch (Exception e) {
            logger.error("AI分类解析异常", e);
        }

        return Optional.empty();
    }

    /**
     * 验证并获取分类（确保层级关系正确）
     */
    private Optional<Category> validateAndGetCategory(CategoryParseResult parseResult, TransactionType expectedType) {
        if (parseResult == null || parseResult.getCategoryId() == null) {
            return Optional.empty();
        }
        return resolveAndValidateCategory(
                parseResult.getCategoryId(), parseResult.getParentCategoryId(), expectedType);
    }

    /**
     * 按 ID + 父 ID 校验分类层级关系并返回分类实体。
     * 供 AI 合并解析流程复用（合并结果只有裸字段，不带 CategoryParseResult）。
     */
    Optional<Category> resolveAndValidateCategory(Long categoryId, Long parentCategoryId) {
        return resolveAndValidateCategory(categoryId, parentCategoryId, null);
    }

    /**
     * 按 ID、父 ID 和预期交易类型校验分类。
     * AI 输出属于不可信输入：即使层级关系正确，也必须确保收入只使用收入分类、支出只使用支出分类。
     */
    Optional<Category> resolveAndValidateCategory(Long categoryId, Long parentCategoryId,
                                                  TransactionType expectedType) {
        if (categoryId == null) {
            return Optional.empty();
        }
        try {
            Optional<Category> categoryOpt = categoryRepository.findById(categoryId);
            if (categoryOpt.isEmpty()) {
                logger.warn("AI返回的分类ID不存在: {}", categoryId);
                return Optional.empty();
            }

            Category category = categoryOpt.get();

            if (expectedType != null && category.getType() != expectedType) {
                logger.warn("分类类型与交易类型不一致: categoryId={}, categoryType={}, transactionType={}",
                        categoryId, category.getType(), expectedType);
                return Optional.empty();
            }

            // 如果AI返回了父分类信息，验证层级关系
            if (parentCategoryId != null) {
                // 这应该是一个二级分类
                if (category.getLevel() != 2) {
                    logger.warn("AI返回了父分类ID，但选择的分类不是二级分类: {} (level: {})",
                            category.getName(), category.getLevel());
                    return Optional.empty();
                }

                // 验证父分类关系
                if (category.getParent() == null) {
                    logger.warn("二级分类没有父分类: {}", category.getName());
                    return Optional.empty();
                }

                if (!category.getParent().getId().equals(parentCategoryId)) {
                    logger.warn("AI返回的父分类ID与实际不符: AI返回={}, 实际={}, 分类={}",
                            parentCategoryId,
                            category.getParent().getId(),
                            category.getName());
                    return Optional.empty();
                }

                logger.debug("分类层级关系验证通过: {} > {}", category.getParent().getName(), category.getName());
            } else {
                // 没有父分类信息，应该是一级分类
                if (category.getLevel() != 1) {
                    logger.warn("AI没有返回父分类ID，但选择的不是一级分类: {} (level: {})",
                            category.getName(), category.getLevel());
                    // 如果是二级分类但AI没返回父分类，仍然接受，但记录警告
                    logger.debug("接受二级分类，但AI未返回父分类信息: {}", category.getName());
                }
            }

            return Optional.of(category);

        } catch (Exception e) {
            logger.error("验证分类层级关系时出错", e);
            return Optional.empty();
        }
    }
    
    /**
     * 使用传统规则匹配分类
     */
    Optional<Category> findCategoryWithRules(String description, TransactionType type) {
        logger.debug("使用规则匹配分类: {} ({})", description, type);
        
        String lowerDesc = description.toLowerCase();
        
        // 先尝试匹配二级分类（更精确）
        List<Category> subCategories = categoryRepository.findByTypeAndLevel(type, 2);
        
        // 优先级匹配关键词
        for (Category category : subCategories) {
            String categoryName = category.getName().toLowerCase();
            if (lowerDesc.contains(categoryName)) {
                logger.debug("精确匹配分类: {}", category);
                return Optional.of(category);
            }
        }
        
        // 使用更智能的关键词匹配
        Category bestMatch = findCategoryByKeywords(lowerDesc, subCategories);
        if (bestMatch != null) {
            logger.debug("关键词匹配分类: {}", bestMatch);
            return Optional.of(bestMatch);
        }
        
        // 如果没有找到匹配的二级分类，尝试一级分类
        List<Category> topCategories = categoryRepository.findByTypeAndLevel(type, 1);
        bestMatch = findCategoryByKeywords(lowerDesc, topCategories);
        if (bestMatch != null) {
            logger.debug("一级分类匹配: {}", bestMatch);
            return Optional.of(bestMatch);
        }
        
        // 最后回退到默认分类
        if (!topCategories.isEmpty()) {
            Category defaultCategory = topCategories.get(0);
            logger.debug("使用默认分类: {}", defaultCategory);
            return Optional.of(defaultCategory);
        }
        
        return Optional.empty();
    }
    
    /**
     * 构建全类目树文本（支出 + 收入），供 AI 合并解析提示词使用。
     * 一次 JOIN FETCH 取整棵两级树，避免逐父分类懒加载 N+1。
     * TRANSFER 哨兵分类不进树（转账走单独流程，不参与 AI 分类）。
     */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "categoryTree")
    public String getFullCategoryTreeText() {
        List<Category> level1 = categoryRepository.findAllLevel1WithChildren();
        StringBuilder expense = new StringBuilder();
        StringBuilder income = new StringBuilder();
        for (Category c : level1) {
            if (c.getType() == TransactionType.INCOME) {
                appendCategoryNode(income, c);
            } else if (c.getType() == TransactionType.EXPENSE) {
                appendCategoryNode(expense, c);
            }
        }
        StringBuilder result = new StringBuilder();
        result.append("【支出分类树】\n").append(expense);
        result.append("\n【收入分类树】\n").append(income);
        return result.toString();
    }

    /**
     * 渲染单个一级分类及其二级子分类节点。
     */
    private void appendCategoryNode(StringBuilder sb, Category category) {
        sb.append("\n【").append(category.getName())
                .append("】 (一级分类ID: ").append(category.getId()).append(")\n");
        if (category.getChildren() != null && !category.getChildren().isEmpty()) {
            for (Category child : category.getChildren()) {
                sb.append("  └─ ").append(child.getName())
                        .append(" (二级分类ID: ").append(child.getId()).append(")\n");
            }
        } else {
            sb.append("  └─ (无二级分类)\n");
        }
    }

    /**
     * 构建AI分类解析提示词
     */
    private String buildCategoryPrompt(String description, TransactionType type, String originalText) {
        // 获取所有相关分类信息
        List<Category> allCategories = categoryRepository.findByType(type);

        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个专业的财务分类助手，请根据交易信息从分类树中选择最合适的分类。\n\n");
        if (originalText != null && !originalText.equals(description)) {
            prompt.append("用户原始输入: ").append(originalText).append("\n");
        }
        prompt.append("交易描述: ").append(description).append("\n");
        prompt.append("交易类型: ").append(type.name()).append("\n\n");
        prompt.append("分类树结构（一级分类 > 二级分类）:\n");

        // 构建分类树形结构
        for (Category category : allCategories) {
            if (category.getLevel() == 1) {
                prompt.append("\n【").append(category.getName()).append("】 (一级分类ID: ").append(category.getId()).append(")\n");
                if (category.getChildren() != null && !category.getChildren().isEmpty()) {
                    for (Category child : category.getChildren()) {
                        prompt.append("  └─ ").append(child.getName()).append(" (二级分类ID: ").append(child.getId()).append(")\n");
                    }
                } else {
                    prompt.append("  └─ (无二级分类)\n");
                }
            }
        }

        prompt.append("\n请返回以下格式的JSON:\n");
        prompt.append("{\n");
        prompt.append("  \"categoryId\": 最终选择的分类ID（优先二级分类，如果没有合适的二级分类则返回一级分类ID）,\n");
        prompt.append("  \"categoryName\": \"最终选择的分类名称\",\n");
        prompt.append("  \"parentCategoryId\": 一级分类ID（如果选择的是二级分类，必须填写其父分类ID；如果选择的是一级分类，则为null）,\n");
        prompt.append("  \"parentCategoryName\": \"一级分类名称\"（如果选择的是二级分类，必须填写其父分类名称；如果选择的是一级分类，则为null）,\n");
        prompt.append("  \"confidence\": 置信度(0-1),\n");
        prompt.append("  \"reason\": \"选择理由\"\n");
        prompt.append("}\n\n");
        prompt.append("【重要规则】:\n");
        prompt.append("1. 分类层级关系必须正确：如果返回二级分类，parentCategoryId 和 parentCategoryName 必须是该二级分类真实的父分类\n");
        prompt.append("2. 不能随意组合一级和二级分类：二级分类必须在对应的一级分类下\n");
        prompt.append("3. 优先选择二级分类（更精确），但必须确保二级分类在正确的一级分类下\n");
        prompt.append("4. 如果无法确定具体的二级分类，只返回一级分类ID，此时 parentCategoryId 和 parentCategoryName 为 null\n");
        prompt.append("5. 置信度要客观评估：精确匹配0.9-1.0，模糊匹配0.6-0.8，不确定0.3-0.5\n");
        prompt.append("6. 只返回JSON，不要其他内容\n\n");
        prompt.append("示例1（选择二级分类）:\n");
        prompt.append("交易描述: \"在星巴克买了一杯咖啡\"\n");
        prompt.append("返回: {\"categoryId\": 123, \"categoryName\": \"咖啡\", \"parentCategoryId\": 100, \"parentCategoryName\": \"餐饮\", \"confidence\": 0.95, \"reason\": \"明确提到星巴克和咖啡\"}\n\n");
        prompt.append("示例2（只能确定一级分类）:\n");
        prompt.append("交易描述: \"买了一些吃的\"\n");
        prompt.append("返回: {\"categoryId\": 100, \"categoryName\": \"餐饮\", \"parentCategoryId\": null, \"parentCategoryName\": null, \"confidence\": 0.6, \"reason\": \"只能确定是餐饮相关，无法确定具体子分类\"}\n");

        return prompt.toString();
    }

    /**
     * AI智能分类解析（返回详细结果）
     *
     * @param description 交易描述
     * @param type 交易类型
     * @return 包含分类信息和AI解析结果的DTO
     */
    @Transactional(readOnly = true)
    public Optional<CategoryDto> findCategoryWithAIAndDetails(String description, TransactionType type) {
        logger.debug("AI智能分类解析: {} ({})", description, type);
        
        try {
            // 构建包含所有分类信息的提示词
            String prompt = buildCategoryPrompt(description, type, null);

            // 调用AI服务，结构化解析为分类结果
            CategoryParseResult parseResult = aiParserService.parseStructured(prompt, CategoryParseResult.class);

            logger.debug("AI分类解析结果: {}", parseResult);

            if (parseResult != null && parseResult.getCategoryId() != null) {
                Optional<Category> category = categoryRepository.findById(parseResult.getCategoryId());
                if (category.isPresent()) {
                    CategoryDto dto = convertToDto(category.get());
                    dto.setConfidence(parseResult.getConfidence());
                    dto.setReason(parseResult.getReason());
                    return Optional.of(dto);
                }
            }
            
        } catch (Exception e) {
            logger.error("AI分类解析异常", e);
        }
        
        return Optional.empty();
    }
    
    /**
     * 根据关键词匹配分类
     */
    private Category findCategoryByKeywords(String description, List<Category> categories) {
        // 定义关键词映射
        for (Category category : categories) {
            if (matchCategoryKeywords(description, category.getName())) {
                return category;
            }
        }
        return null;
    }
    
    /**
     * 匹配分类关键词
     */
    private boolean matchCategoryKeywords(String description, String categoryName) {
        String lowerCategoryName = categoryName.toLowerCase();
        
        // 根据分类名称定义关键词
        switch (lowerCategoryName) {
            case "早餐":
                return description.contains("早餐") || description.contains("早饭") || 
                       description.contains("豆浆") || description.contains("包子") || description.contains("油条");
            case "午餐":
                return description.contains("午餐") || description.contains("午饭") || description.contains("中餐");
            case "晚餐":
                return description.contains("晚餐") || description.contains("晚饭") || description.contains("晚上吃");
            case "零食饮料":
                return description.contains("咖啡") || description.contains("奶茶") || description.contains("饮料") ||
                       description.contains("零食") || description.contains("星巴克") || description.contains("coco") ||
                       description.contains("喜茶") || description.contains("茶颜悦色");
            case "出租车":
                return description.contains("打车") || description.contains("滴滴") || description.contains("出租车") ||
                       description.contains("网约车") || description.contains("uber");
            case "公交地铁":
                return description.contains("地铁") || description.contains("公交") || description.contains("公共交通") ||
                       description.contains("车票") || description.contains("交通卡");
            case "电影演出":
                return description.contains("电影") || description.contains("电影票") || description.contains("演出") ||
                       description.contains("话剧") || description.contains("音乐会") || description.contains("看电影");
            case "购物":
                return description.contains("购物") || description.contains("买") || description.contains("商场") ||
                       description.contains("超市") || description.contains("淘宝") || description.contains("京东");
            case "房租":
                return description.contains("房租") || description.contains("租房") || description.contains("租金");
            case "水电费":
                return description.contains("水费") || description.contains("电费") || description.contains("水电费") ||
                       description.contains("燃气费");
            default:
                return description.contains(lowerCategoryName);
        }
    }
    
    /**
     * 转换为DTO
     */
    public CategoryDto convertToDto(Category category) {
        CategoryDto dto = new CategoryDto();
        dto.setId(category.getId());
        dto.setName(category.getName());
        dto.setLevel(category.getLevel());
        dto.setType(category.getType());
        dto.setDescription(category.getDescription());
        
        if (category.getParent() != null) {
            dto.setParentId(category.getParent().getId());
            dto.setParentName(category.getParent().getName());
        }
        
        if (category.getChildren() != null && !category.getChildren().isEmpty()) {
            List<CategoryDto> childrenDto = category.getChildren().stream()
                    .map(this::convertToDto)
                    .collect(Collectors.toList());
            dto.setChildren(childrenDto);
        }
        
        return dto;
    }
    
    /**
     * 转换为实体
     */
    private Category convertToEntity(CategoryDto dto) {
        Category category = new Category();
        category.setName(dto.getName());
        category.setLevel(dto.getLevel());
        category.setType(dto.getType());
        category.setDescription(dto.getDescription());
        
        if (dto.getParentId() != null) {
            Category parent = categoryRepository.findById(dto.getParentId())
                    .orElseThrow(() -> new BusinessException("父分类不存在: " + dto.getParentId()));
            category.setParent(parent);
        }
        
        return category;
    }
    
    /**
     * 从DTO更新实体
     */
    private void updateEntityFromDto(Category category, CategoryDto dto) {
        category.setName(dto.getName());
        category.setDescription(dto.getDescription());

        if (dto.getParentId() != null && !dto.getParentId().equals(
                category.getParent() != null ? category.getParent().getId() : null)) {
            Category parent = categoryRepository.findById(dto.getParentId())
                    .orElseThrow(() -> new BusinessException("父分类不存在: " + dto.getParentId()));
            category.setParent(parent);
        }
    }

    /**
     * 构建 CSV 导入用的分类名称索引：一次 JOIN FETCH 取整棵两级树，
     * 消除导入循环里逐行查询分类的开销。匹配语义与原逐行查询一致：
     * 仅匹配二级分类；「父分类名+子分类名」精确匹配优先，父分类未匹配或未提供时
     * 回退到同名子分类的第一个候选。
     */
    @Transactional(readOnly = true)
    public CategoryNameIndex buildCategoryNameIndex() {
        List<Category> level1 = categoryRepository.findAllLevel1WithChildren();
        Map<String, Category> exactByParentAndChild = new HashMap<>();
        Map<String, Category> firstByChildName = new HashMap<>();
        for (Category parent : level1) {
            if (parent.getChildren() == null) {
                continue;
            }
            for (Category child : parent.getChildren()) {
                exactByParentAndChild.putIfAbsent(
                        CategoryNameIndex.key(child.getType(), parent.getName(), child.getName()), child);
                firstByChildName.putIfAbsent(
                        CategoryNameIndex.key(child.getType(), null, child.getName()), child);
            }
        }
        return new CategoryNameIndex(exactByParentAndChild, firstByChildName);
    }

    /**
     * 分类名称查找索引（导入预载）。
     */
    public static final class CategoryNameIndex {

        private final Map<String, Category> exactByParentAndChild;
        private final Map<String, Category> firstByChildName;

        private CategoryNameIndex(Map<String, Category> exactByParentAndChild,
                                  Map<String, Category> firstByChildName) {
            this.exactByParentAndChild = exactByParentAndChild;
            this.firstByChildName = firstByChildName;
        }

        /**
         * 根据父分类名+子分类名查找分类；未匹配返回 null（由调用方决定保留原分类或报错）。
         */
        public Category find(String parentName, String childName, TransactionType type) {
            if (childName == null || childName.trim().isEmpty()) {
                return null;
            }
            String child = childName.trim();
            if (parentName != null && !parentName.trim().isEmpty()) {
                Category exact = exactByParentAndChild.get(key(type, parentName.trim(), child));
                if (exact != null) {
                    return exact;
                }
            }
            return firstByChildName.get(key(type, null, child));
        }

        private static String key(TransactionType type, String parentName, String childName) {
            return type.name() + '\n' + (parentName == null ? "" : parentName) + '\n' + childName;
        }
    }
}
