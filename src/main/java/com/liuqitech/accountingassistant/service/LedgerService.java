package com.liuqitech.accountingassistant.service;

import com.liuqitech.accountingassistant.dto.CreateLedgerRequest;
import com.liuqitech.accountingassistant.dto.InviteMemberRequest;
import com.liuqitech.accountingassistant.dto.LedgerDto;
import com.liuqitech.accountingassistant.dto.LedgerMemberDto;
import com.liuqitech.accountingassistant.dto.UpdateLedgerRequest;
import com.liuqitech.accountingassistant.entity.Ledger;
import com.liuqitech.accountingassistant.entity.LedgerMember;
import com.liuqitech.accountingassistant.entity.User;
import com.liuqitech.accountingassistant.enums.LedgerRole;
import com.liuqitech.accountingassistant.exception.BusinessException;
import com.liuqitech.accountingassistant.exception.ResourceNotFoundException;
import com.liuqitech.accountingassistant.repository.AccountRepository;
import com.liuqitech.accountingassistant.repository.AnalysisChatContextRepository;
import com.liuqitech.accountingassistant.repository.AnalysisChatMessageRepository;
import com.liuqitech.accountingassistant.repository.CategoryCorrectionRepository;
import com.liuqitech.accountingassistant.repository.LedgerMemberRepository;
import com.liuqitech.accountingassistant.repository.LedgerRepository;
import com.liuqitech.accountingassistant.repository.RecurringBillRepository;
import com.liuqitech.accountingassistant.repository.TagRepository;
import com.liuqitech.accountingassistant.repository.TransactionRepository;
import com.liuqitech.accountingassistant.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 账本与成员管理服务。
 *
 * <p>邀请采用「直接添加已注册用户名 + 角色」的方式，立即生效，无需对方确认。
 * OWNER 级操作在本服务内用 {@link LedgerAccessService#requireAccess} 兜底校验
 * （/api/ledgers/** 不经过 LedgerContextInterceptor）。</p>
 */
@Service
public class LedgerService {

    private static final Logger logger = LoggerFactory.getLogger(LedgerService.class);

    private static final String DEFAULT_LEDGER_NAME = "我的账本";
    private static final String DEFAULT_LEDGER_ICON = "📒";
    private static final String DEFAULT_LEDGER_COLOR = "#6366F1";

    private final LedgerRepository ledgerRepository;
    private final LedgerMemberRepository ledgerMemberRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final TagRepository tagRepository;
    private final AccountRepository accountRepository;
    private final CategoryCorrectionRepository categoryCorrectionRepository;
    private final RecurringBillRepository recurringBillRepository;
    private final AnalysisChatMessageRepository analysisChatMessageRepository;
    private final AnalysisChatContextRepository analysisChatContextRepository;
    private final LedgerAccessService ledgerAccessService;

    public LedgerService(LedgerRepository ledgerRepository,
                         LedgerMemberRepository ledgerMemberRepository,
                         UserRepository userRepository,
                         TransactionRepository transactionRepository,
                         TagRepository tagRepository,
                         AccountRepository accountRepository,
                         CategoryCorrectionRepository categoryCorrectionRepository,
                         RecurringBillRepository recurringBillRepository,
                         AnalysisChatMessageRepository analysisChatMessageRepository,
                         AnalysisChatContextRepository analysisChatContextRepository,
                         LedgerAccessService ledgerAccessService) {
        this.ledgerRepository = ledgerRepository;
        this.ledgerMemberRepository = ledgerMemberRepository;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.tagRepository = tagRepository;
        this.accountRepository = accountRepository;
        this.categoryCorrectionRepository = categoryCorrectionRepository;
        this.recurringBillRepository = recurringBillRepository;
        this.analysisChatMessageRepository = analysisChatMessageRepository;
        this.analysisChatContextRepository = analysisChatContextRepository;
        this.ledgerAccessService = ledgerAccessService;
    }

    // ==================== 默认账本（注册时调用） ====================

    /**
     * 为用户创建默认账本并建立 OWNER 成员行，返回该账本。调用方负责回写 User.defaultLedgerId。
     */
    @Transactional
    public Ledger createDefaultLedger(String username) {
        Ledger ledger = new Ledger(DEFAULT_LEDGER_NAME, username);
        ledger.setDefault(true);
        ledger.setIcon(DEFAULT_LEDGER_ICON);
        ledger.setColor(DEFAULT_LEDGER_COLOR);
        ledger = ledgerRepository.save(ledger);
        ledgerMemberRepository.save(new LedgerMember(ledger.getId(), username, LedgerRole.OWNER));
        logger.info("为用户 [{}] 创建默认账本 id={}", username, ledger.getId());
        return ledger;
    }

    /**
     * 找到或创建某用户名的默认账本，并确保存在 OWNER 成员行（迁移/兜底用，幂等）。
     */
    @Transactional
    public Ledger ensureDefaultLedger(String username) {
        List<Ledger> owned = ledgerRepository.findByOwnerUsername(username);
        Ledger ledger = owned.stream().filter(Ledger::isDefault).findFirst()
                .orElse(owned.isEmpty() ? null : owned.get(0));
        if (ledger == null) {
            return createDefaultLedger(username);
        }
        if (!ledgerMemberRepository.existsByLedgerIdAndUsername(ledger.getId(), username)) {
            ledgerMemberRepository.save(new LedgerMember(ledger.getId(), username, LedgerRole.OWNER));
        }
        return ledger;
    }

    // ==================== 账本 CRUD ====================

    @Transactional
    public LedgerDto createLedger(String username, CreateLedgerRequest request) {
        String name = request.getName() == null ? "" : request.getName().trim();
        if (name.isEmpty()) {
            throw new BusinessException("账本名称不能为空");
        }
        Ledger ledger = new Ledger(name, username);
        ledger.setDescription(request.getDescription());
        ledger.setIcon(request.getIcon());
        ledger.setColor(request.getColor());
        ledger = ledgerRepository.save(ledger);
        ledgerMemberRepository.save(new LedgerMember(ledger.getId(), username, LedgerRole.OWNER));
        logger.info("用户 {} 创建账本: {} (id={})", username, name, ledger.getId());
        return toDto(ledger, LedgerRole.OWNER, ledgerMemberRepository.countByLedgerId(ledger.getId()));
    }

    @Transactional(readOnly = true)
    public List<LedgerDto> listMyLedgers(String username) {
        Long defaultLedgerId = userRepository.findByUsername(username)
                .map(User::getDefaultLedgerId)
                .orElse(null);
        List<LedgerMember> memberships = ledgerMemberRepository.findByUsername(username);
        if (memberships.isEmpty()) {
            return new ArrayList<>();
        }
        // 一次性取角色、账本、成员数，避免逐个账本 N+1 查询
        Map<Long, LedgerRole> roleByLedger = new HashMap<>();
        for (LedgerMember m : memberships) {
            roleByLedger.putIfAbsent(m.getLedgerId(), m.getRole());
        }
        List<Long> ledgerIds = new ArrayList<>(roleByLedger.keySet());
        Map<Long, Long> countByLedger = new HashMap<>();
        for (Object[] row : ledgerMemberRepository.countByLedgerIdIn(ledgerIds)) {
            countByLedger.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        List<LedgerDto> result = new ArrayList<>();
        for (Ledger ledger : ledgerRepository.findAllById(ledgerIds)) {
            result.add(toDto(ledger, roleByLedger.get(ledger.getId()),
                    countByLedger.getOrDefault(ledger.getId(), 0L),
                    ledger.getId().equals(defaultLedgerId)));
        }
        // 默认账本优先，其次按 id
        result.sort(Comparator.comparing(LedgerDto::isDefault).reversed()
                .thenComparing(LedgerDto::getId));
        return result;
    }

    @Transactional(readOnly = true)
    public LedgerDto getLedger(String username, Long ledgerId) {
        LedgerMember member = ledgerAccessService.requireAccess(username, ledgerId, LedgerRole.VIEWER);
        Ledger ledger = ledgerRepository.findById(ledgerId)
                .orElseThrow(() -> new ResourceNotFoundException("账本不存在"));
        Long defaultLedgerId = userRepository.findByUsername(username)
                .map(User::getDefaultLedgerId)
                .orElse(null);
        return toDto(ledger, member.getRole(), ledgerMemberRepository.countByLedgerId(ledgerId),
                ledger.getId().equals(defaultLedgerId));
    }

    @Transactional
    public LedgerDto updateLedger(String username, Long ledgerId, UpdateLedgerRequest request) {
        ledgerAccessService.requireAccess(username, ledgerId, LedgerRole.OWNER);
        Ledger ledger = ledgerRepository.findById(ledgerId)
                .orElseThrow(() -> new ResourceNotFoundException("账本不存在"));

        if (request.getName() != null) {
            String name = request.getName().trim();
            if (!name.isEmpty()) {
                ledger.setName(name);
            }
        }
        if (request.getDescription() != null) {
            ledger.setDescription(request.getDescription());
        }
        if (request.getIcon() != null) {
            ledger.setIcon(request.getIcon());
        }
        if (request.getColor() != null) {
            ledger.setColor(request.getColor());
        }
        ledger = ledgerRepository.save(ledger);
        logger.info("用户 {} 更新账本 {}", username, ledgerId);
        Long defaultLedgerId = userRepository.findByUsername(username)
                .map(User::getDefaultLedgerId)
                .orElse(null);
        return toDto(ledger, LedgerRole.OWNER, ledgerMemberRepository.countByLedgerId(ledger.getId()),
                ledger.getId().equals(defaultLedgerId));
    }

    /**
     * 删除账本（OWNER）：级联删除其交易、标签、纠正记忆、分析聊天、成员行；并把以它为默认账本的用户重新指向其它账本。
     */
    @Transactional
    public void deleteLedger(String username, Long ledgerId) {
        ledgerAccessService.requireAccess(username, ledgerId, LedgerRole.OWNER);

        recurringBillRepository.deleteByLedgerId(ledgerId);
        transactionRepository.deleteByLedgerId(ledgerId);
        accountRepository.deleteByLedgerId(ledgerId);
        tagRepository.deleteByLedgerId(ledgerId);
        categoryCorrectionRepository.deleteByLedgerId(ledgerId);
        analysisChatMessageRepository.deleteByLedgerId(ledgerId);
        analysisChatContextRepository.deleteByLedgerId(ledgerId);
        ledgerMemberRepository.deleteByLedgerId(ledgerId);
        ledgerRepository.deleteById(ledgerId);

        // 重新指向默认账本
        for (User user : userRepository.findByDefaultLedgerId(ledgerId)) {
            Long replacement = ledgerMemberRepository.findByUsername(user.getUsername()).stream()
                    .map(LedgerMember::getLedgerId)
                    .findFirst()
                    .orElse(null);
            user.setDefaultLedgerId(replacement);
            userRepository.save(user);
        }
        logger.info("用户 {} 删除账本 {}（级联清理完成）", username, ledgerId);
    }

    // ==================== 成员管理 ====================

    @Transactional(readOnly = true)
    public List<LedgerMemberDto> listMembers(String username, Long ledgerId) {
        ledgerAccessService.requireAccess(username, ledgerId, LedgerRole.VIEWER);
        List<LedgerMemberDto> members = new ArrayList<>();
        for (LedgerMember m : ledgerMemberRepository.findByLedgerId(ledgerId)) {
            members.add(new LedgerMemberDto(m.getUsername(), m.getRole(), m.getCreatedAt()));
        }
        // 所有者优先，其次按加入时间
        members.sort(Comparator
                .comparing((LedgerMemberDto m) -> m.getRole() != LedgerRole.OWNER)
                .thenComparing(LedgerMemberDto::getJoinedAt, Comparator.nullsLast(Comparator.naturalOrder())));
        return members;
    }

    @Transactional
    public LedgerMemberDto inviteMember(String username, Long ledgerId, InviteMemberRequest request) {
        ledgerAccessService.requireAccess(username, ledgerId, LedgerRole.OWNER);

        String target = request.getUsername() == null ? "" : request.getUsername().trim();
        if (target.isEmpty()) {
            throw new BusinessException("用户名不能为空");
        }
        LedgerRole role = request.getRole();
        if (role == null || role == LedgerRole.OWNER) {
            throw new BusinessException("邀请角色只能是「可编辑」或「仅查看」");
        }
        if (!userRepository.existsByUsername(target)) {
            throw new BusinessException("用户不存在: " + target);
        }
        if (ledgerMemberRepository.existsByLedgerIdAndUsername(ledgerId, target)) {
            throw new BusinessException("该用户已是账本成员");
        }

        LedgerMember member = ledgerMemberRepository.save(new LedgerMember(ledgerId, target, role));
        logger.info("用户 {} 邀请 {} 加入账本 {}，角色 {}", username, target, ledgerId, role);
        return new LedgerMemberDto(member.getUsername(), member.getRole(), member.getCreatedAt());
    }

    @Transactional
    public LedgerMemberDto updateMemberRole(String username, Long ledgerId, String targetUsername, LedgerRole role) {
        ledgerAccessService.requireAccess(username, ledgerId, LedgerRole.OWNER);

        if (role == null || role == LedgerRole.OWNER) {
            throw new BusinessException("角色只能改为「可编辑」或「仅查看」；如需移交所有权请使用转让功能");
        }
        if (username.equals(targetUsername)) {
            throw new BusinessException("不能修改自己的角色");
        }
        LedgerMember member = ledgerMemberRepository.findByLedgerIdAndUsername(ledgerId, targetUsername)
                .orElseThrow(() -> new ResourceNotFoundException("成员不存在: " + targetUsername));
        if (member.getRole() == LedgerRole.OWNER) {
            throw new BusinessException("不能修改所有者的角色");
        }
        member.setRole(role);
        member = ledgerMemberRepository.save(member);
        logger.info("用户 {} 将账本 {} 成员 {} 角色改为 {}", username, ledgerId, targetUsername, role);
        return new LedgerMemberDto(member.getUsername(), member.getRole(), member.getCreatedAt());
    }

    @Transactional
    public void removeMember(String username, Long ledgerId, String targetUsername) {
        ledgerAccessService.requireAccess(username, ledgerId, LedgerRole.OWNER);

        if (username.equals(targetUsername)) {
            throw new BusinessException("不能移除自己；如需退出请使用退出功能，所有者请先转让或删除账本");
        }
        LedgerMember member = ledgerMemberRepository.findByLedgerIdAndUsername(ledgerId, targetUsername)
                .orElseThrow(() -> new ResourceNotFoundException("成员不存在: " + targetUsername));
        if (member.getRole() == LedgerRole.OWNER) {
            throw new BusinessException("不能移除所有者");
        }
        ledgerMemberRepository.delete(member);
        clearDefaultIfMatches(targetUsername, ledgerId);
        logger.info("用户 {} 将 {} 移出账本 {}", username, targetUsername, ledgerId);
    }

    /**
     * 当前用户退出账本（非所有者）。
     */
    @Transactional
    public void leaveLedger(String username, Long ledgerId) {
        LedgerMember member = ledgerAccessService.resolveMembership(username, ledgerId);
        if (member.getRole() == LedgerRole.OWNER) {
            throw new BusinessException("所有者不能退出账本，请先转让所有权或删除账本");
        }
        ledgerMemberRepository.delete(member);
        clearDefaultIfMatches(username, ledgerId);
        logger.info("用户 {} 退出账本 {}", username, ledgerId);
    }

    /**
     * 转让所有权（OWNER）：新所有者升为 OWNER，原所有者降为 EDITOR，更新账本 owner_username。
     */
    @Transactional
    public void transferOwnership(String username, Long ledgerId, String targetUsername) {
        ledgerAccessService.requireAccess(username, ledgerId, LedgerRole.OWNER);

        if (username.equals(targetUsername)) {
            throw new BusinessException("不能转让给自己");
        }
        LedgerMember target = ledgerMemberRepository.findByLedgerIdAndUsername(ledgerId, targetUsername)
                .orElseThrow(() -> new ResourceNotFoundException("成员不存在: " + targetUsername));
        LedgerMember current = ledgerMemberRepository.findByLedgerIdAndUsername(ledgerId, username)
                .orElseThrow(() -> new ResourceNotFoundException("成员不存在"));

        target.setRole(LedgerRole.OWNER);
        current.setRole(LedgerRole.EDITOR);
        ledgerMemberRepository.save(target);
        ledgerMemberRepository.save(current);

        Ledger ledger = ledgerRepository.findById(ledgerId)
                .orElseThrow(() -> new ResourceNotFoundException("账本不存在"));
        ledger.setOwnerUsername(targetUsername);
        ledgerRepository.save(ledger);
        logger.info("用户 {} 将账本 {} 所有权转让给 {}", username, ledgerId, targetUsername);
    }

    // ==================== 默认账本 ====================

    @Transactional
    public void setDefaultLedger(String username, Long ledgerId) {
        ledgerAccessService.resolveMembership(username, ledgerId); // 校验成员身份
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在"));
        user.setDefaultLedgerId(ledgerId);
        userRepository.save(user);
        logger.info("用户 {} 将账本 {} 设为默认账本", username, ledgerId);
    }

    /**
     * 兼容旧接口：历史上「当前账本」实际写入的是用户默认账本。
     */
    @Transactional
    public void setActiveLedger(String username, Long ledgerId) {
        setDefaultLedger(username, ledgerId);
    }

    // ==================== 内部辅助 ====================

    private void clearDefaultIfMatches(String username, Long ledgerId) {
        userRepository.findByUsername(username).ifPresent(user -> {
            if (ledgerId.equals(user.getDefaultLedgerId())) {
                Long replacement = ledgerMemberRepository.findByUsername(username).stream()
                        .map(LedgerMember::getLedgerId)
                        .findFirst()
                        .orElse(null);
                user.setDefaultLedgerId(replacement);
                userRepository.save(user);
            }
        });
    }

    private LedgerDto toDto(Ledger ledger, LedgerRole myRole, long memberCount) {
        return toDto(ledger, myRole, memberCount, ledger.isDefault());
    }

    private LedgerDto toDto(Ledger ledger, LedgerRole myRole, long memberCount, boolean isDefault) {
        LedgerDto dto = new LedgerDto();
        dto.setId(ledger.getId());
        dto.setName(ledger.getName());
        dto.setDescription(ledger.getDescription());
        dto.setIcon(ledger.getIcon());
        dto.setColor(ledger.getColor());
        dto.setMyRole(myRole);
        dto.setMemberCount(memberCount);
        dto.setOwner(myRole == LedgerRole.OWNER);
        dto.setDefault(isDefault);
        dto.setCreatedAt(ledger.getCreatedAt());
        return dto;
    }
}
