package com.liuqitech.accountingassistant.controller;

import com.liuqitech.accountingassistant.dto.ApiResponse;
import com.liuqitech.accountingassistant.dto.CreateTagRequest;
import com.liuqitech.accountingassistant.dto.TagDto;
import com.liuqitech.accountingassistant.dto.UpdateTagRequest;
import com.liuqitech.accountingassistant.interceptor.LedgerContext;
import com.liuqitech.accountingassistant.service.TagService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 标签管理控制器（标签归属当前账本，成员共享）
 */
@RestController
@RequestMapping("/api/tags")
public class TagController {

    private static final Logger logger = LoggerFactory.getLogger(TagController.class);

    @Autowired
    private TagService tagService;

    /**
     * 获取当前账本的所有标签
     */
    @GetMapping
    public ApiResponse<List<TagDto>> getTags(HttpServletRequest request) {
        Long ledgerId = LedgerContext.ledgerId(request);
        logger.debug("获取账本 {} 的标签列表", ledgerId);
        List<TagDto> tags = tagService.getLedgerTags(ledgerId);
        return ApiResponse.success(tags);
    }

    /**
     * 创建标签
     */
    @PostMapping
    public ApiResponse<TagDto> createTag(@Valid @RequestBody CreateTagRequest req,
                                         HttpServletRequest request) {
        Long ledgerId = LedgerContext.ledgerId(request);
        String username = LedgerContext.username(request);
        logger.info("账本 {} 创建标签: {}", ledgerId, req.getName());
        TagDto tag = tagService.createTag(ledgerId, username, req);
        return ApiResponse.success(tag);
    }

    /**
     * 更新标签
     */
    @PutMapping("/{id}")
    public ApiResponse<TagDto> updateTag(@PathVariable Long id,
                                         @Valid @RequestBody UpdateTagRequest req,
                                         HttpServletRequest request) {
        Long ledgerId = LedgerContext.ledgerId(request);
        logger.info("账本 {} 更新标签 {}", ledgerId, id);
        TagDto tag = tagService.updateTag(ledgerId, id, req);
        return ApiResponse.success(tag);
    }

    /**
     * 删除标签
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteTag(@PathVariable Long id, HttpServletRequest request) {
        Long ledgerId = LedgerContext.ledgerId(request);
        logger.info("账本 {} 删除标签 {}", ledgerId, id);
        tagService.deleteTag(ledgerId, id);
        return ApiResponse.success(null);
    }
}
