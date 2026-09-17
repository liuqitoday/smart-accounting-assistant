package com.liuqitech.accountingassistant.service;

import com.liuqitech.accountingassistant.dto.CreateTagRequest;
import com.liuqitech.accountingassistant.dto.TagDto;
import com.liuqitech.accountingassistant.dto.UpdateTagRequest;
import com.liuqitech.accountingassistant.entity.Tag;
import com.liuqitech.accountingassistant.exception.BusinessException;
import com.liuqitech.accountingassistant.exception.ResourceNotFoundException;
import com.liuqitech.accountingassistant.repository.TagRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 标签服务
 */
@Service
public class TagService {

    private static final Logger logger = LoggerFactory.getLogger(TagService.class);

    /** 系统预置标签的归属哨兵值（非真实用户，注册时禁止占用） */
    public static final String SYSTEM_OWNER = "__system__";

    @Autowired
    private TagRepository tagRepository;

    /**
     * 获取账本的所有可用标签（全局系统标签 + 该账本的标签）
     */
    @Transactional(readOnly = true)
    public List<TagDto> getLedgerTags(Long ledgerId) {
        logger.debug("获取账本 {} 的所有标签", ledgerId);
        List<Tag> tags = new ArrayList<>(tagRepository.findBySystemTrueOrderByNameAsc());
        tags.addAll(tagRepository.findByLedgerIdOrderByNameAsc(ledgerId));
        return tags.stream().map(this::convertToDto).collect(Collectors.toList());
    }

    /**
     * 创建标签（归属当前账本，成员共享）
     */
    @Transactional
    public TagDto createTag(Long ledgerId, String username, CreateTagRequest request) {
        logger.debug("用户 {} 在账本 {} 创建标签: {}", username, ledgerId, request.getName());

        String name = request.getName().trim();
        if (name.isEmpty()) {
            throw new BusinessException("标签名称不能为空");
        }

        // 不能与系统预置标签重名
        if (tagRepository.existsByNameIgnoreCaseAndSystemTrue(name)) {
            throw new BusinessException("「" + name + "」是系统预置标签，无需重复创建");
        }

        // 校验账本内名称不重复
        if (tagRepository.existsByNameAndLedgerId(name, ledgerId)) {
            throw new BusinessException("标签已存在: " + name);
        }

        Tag tag = new Tag();
        tag.setName(name);
        tag.setColor(request.getColor());
        tag.setLedgerId(ledgerId);
        tag.setCreatedBy(username);

        Tag saved = tagRepository.save(tag);
        logger.info("账本 {} 创建标签成功: {}", ledgerId, saved.getName());
        return convertToDto(saved);
    }

    /**
     * 更新标签
     */
    @Transactional
    public TagDto updateTag(Long ledgerId, Long tagId, UpdateTagRequest request) {
        logger.debug("账本 {} 更新标签 {}", ledgerId, tagId);

        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new ResourceNotFoundException("标签不存在: " + tagId));

        // 系统预置标签不可修改
        if (tag.isSystem()) {
            throw new BusinessException("系统预置标签不可修改");
        }

        // 归属校验：不属于当前账本的标签按不存在处理（404），不暴露其他账本资源的存在性
        if (!ledgerId.equals(tag.getLedgerId())) {
            throw new ResourceNotFoundException("标签不存在: " + tagId);
        }

        boolean updated = false;

        // 更新名称
        if (request.getName() != null) {
            String newName = request.getName().trim();
            if (!newName.isEmpty() && !newName.equals(tag.getName())) {
                if (tagRepository.existsByNameIgnoreCaseAndSystemTrue(newName)) {
                    throw new BusinessException("「" + newName + "」是系统预置标签，不能重名");
                }
                if (tagRepository.existsByNameAndLedgerId(newName, ledgerId)) {
                    throw new BusinessException("标签名称已存在: " + newName);
                }
                tag.setName(newName);
                updated = true;
            }
        }

        // 更新颜色
        if (request.getColor() != null) {
            tag.setColor(request.getColor());
            updated = true;
        }

        if (updated) {
            Tag saved = tagRepository.save(tag);
            logger.info("账本 {} 更新标签成功: {}", ledgerId, saved.getName());
            return convertToDto(saved);
        }

        return convertToDto(tag);
    }

    /**
     * 删除标签
     */
    @Transactional
    public void deleteTag(Long ledgerId, Long tagId) {
        logger.debug("账本 {} 删除标签 {}", ledgerId, tagId);

        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new ResourceNotFoundException("标签不存在: " + tagId));

        // 系统预置标签不可删除
        if (tag.isSystem()) {
            throw new BusinessException("系统预置标签不可删除");
        }

        // 归属校验：不属于当前账本的标签按不存在处理（404），不暴露其他账本资源的存在性
        if (!ledgerId.equals(tag.getLedgerId())) {
            throw new ResourceNotFoundException("标签不存在: " + tagId);
        }

        tagRepository.delete(tag);
        logger.info("账本 {} 删除标签成功: {}", ledgerId, tag.getName());
    }

    /**
     * 根据ID查找标签
     */
    @Transactional(readOnly = true)
    public Tag findById(Long tagId) {
        return tagRepository.findById(tagId)
                .orElseThrow(() -> new ResourceNotFoundException("标签不存在: " + tagId));
    }

    /**
     * 批量查询标签并验证归属（避免N+1查询）。
     * 标签合法 = 系统预置标签 或 属于当前账本；不存在或跨账本一律按不存在处理（404），
     * 不暴露其他账本资源的存在性。
     */
    @Transactional(readOnly = true)
    public Set<Tag> findAllByIdsAndLedger(List<Long> tagIds, Long ledgerId) {
        if (tagIds == null || tagIds.isEmpty()) {
            return new HashSet<>();
        }

        List<Tag> tags = tagRepository.findAllById(tagIds);

        // 验证所有标签都存在
        if (tags.size() != tagIds.size()) {
            throw new ResourceNotFoundException("某些标签不存在");
        }

        for (Tag tag : tags) {
            // 系统预置标签所有账本可用；其余必须属于当前账本
            if (!tag.isSystem() && !ledgerId.equals(tag.getLedgerId())) {
                throw new ResourceNotFoundException("某些标签不存在");
            }
        }

        return new HashSet<>(tags);
    }

    /**
     * 预载导入可用的标签映射（系统预置 + 本账本）。
     * 键约定：系统标签按小写名建键（匹配忽略大小写），账本标签按原名建键（精确匹配），
     * 与逐行查库版 getOrCreateTag 的匹配语义一致；导入过程中新建的标签由
     * {@link #getOrCreateTag} 回填映射复用。
     */
    @Transactional(readOnly = true)
    public Map<String, Tag> preloadTagMap(Long ledgerId) {
        Map<String, Tag> tagMap = new HashMap<>();
        for (Tag tag : tagRepository.findByLedgerIdOrderByNameAsc(ledgerId)) {
            tagMap.put(tag.getName(), tag);
        }
        // 系统标签后放，键冲突时优先系统标签（与逐行查库的优先级一致）
        for (Tag tag : tagRepository.findBySystemTrueOrderByNameAsc()) {
            tagMap.put(tag.getName().toLowerCase(Locale.ROOT), tag);
        }
        return tagMap;
    }

    /**
     * 根据名称获取或创建标签（导入时用，归属当前账本）。
     * 只查预载映射不查库：系统标签忽略大小写优先，其次账本标签精确匹配，
     * 未命中才建新标签并回填映射。
     */
    private Tag getOrCreateTag(Long ledgerId, String username, String tagName, Map<String, Tag> tagMap) {
        String name = tagName.trim();
        if (name.isEmpty()) {
            throw new BusinessException("标签名称不能为空");
        }

        Tag systemCandidate = tagMap.get(name.toLowerCase(Locale.ROOT));
        if (systemCandidate != null && systemCandidate.isSystem()) {
            return systemCandidate;
        }
        Tag existing = tagMap.get(name);
        if (existing != null) {
            return existing;
        }

        Tag tag = new Tag();
        tag.setName(name);
        tag.setLedgerId(ledgerId);
        tag.setCreatedBy(username);
        Tag saved = tagRepository.save(tag);
        tagMap.put(name, saved);
        logger.debug("自动创建标签: {} (账本: {})", name, ledgerId);
        return saved;
    }

    /**
     * 解析标签字符串（逗号分隔）。tagMap 为 {@link #preloadTagMap} 预载的映射，
     * 循环内不再逐行查库。
     */
    @Transactional
    public Set<Tag> parseTags(Long ledgerId, String username, String tagsString, Map<String, Tag> tagMap) {
        if (tagsString == null || tagsString.trim().isEmpty()) {
            return new HashSet<>();
        }

        return Arrays.stream(tagsString.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(name -> getOrCreateTag(ledgerId, username, name, tagMap))
                .collect(Collectors.toSet());
    }

    /**
     * 转换为DTO
     */
    public TagDto convertToDto(Tag tag) {
        TagDto dto = new TagDto();
        dto.setId(tag.getId());
        dto.setName(tag.getName());
        dto.setColor(tag.getColor());
        dto.setSystem(tag.isSystem());
        dto.setCreatedAt(tag.getCreatedAt());
        return dto;
    }

    /**
     * 转换标签集合为DTO列表
     */
    public List<TagDto> convertToDtoList(Set<Tag> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        return tags.stream()
                .map(this::convertToDto)
                .sorted((a, b) -> a.getName().compareTo(b.getName()))
                .collect(Collectors.toList());
    }
}
