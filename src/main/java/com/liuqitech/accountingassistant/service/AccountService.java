package com.liuqitech.accountingassistant.service;

import com.liuqitech.accountingassistant.dto.AccountDto;
import com.liuqitech.accountingassistant.dto.CreateAccountRequest;
import com.liuqitech.accountingassistant.dto.UpdateAccountRequest;
import com.liuqitech.accountingassistant.entity.Account;
import com.liuqitech.accountingassistant.entity.LedgerMember;
import com.liuqitech.accountingassistant.enums.AccountType;
import com.liuqitech.accountingassistant.enums.TransactionType;
import com.liuqitech.accountingassistant.exception.BusinessException;
import com.liuqitech.accountingassistant.exception.ResourceNotFoundException;
import com.liuqitech.accountingassistant.repository.AccountRepository;
import com.liuqitech.accountingassistant.repository.LedgerMemberRepository;
import com.liuqitech.accountingassistant.repository.TransactionRepository;
import com.liuqitech.accountingassistant.repository.projection.AnalysisNamedOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.liuqitech.accountingassistant.util.NumberUtils;

/**
 * 账户服务。
 *
 * <p>账户只持久化期初余额，当前余额按「期初 + 关联交易收支净额」实时计算，
 * 不存储可变余额。本服务不依赖 {@code TransactionService}，仅通过
 * {@link TransactionRepository} 做聚合，避免循环依赖。</p>
 *
 * <p>「默认账户」是每个成员在每个账本各自一份的个人偏好（存于 {@code ledger_members.default_account_id}），
 * 只影响新建交易时前端的自动选中，不参与余额计算。</p>
 */
@Service
public class AccountService {

    private static final Logger logger = LoggerFactory.getLogger(AccountService.class);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerMemberRepository ledgerMemberRepository;

    public AccountService(AccountRepository accountRepository,
                          TransactionRepository transactionRepository,
                          LedgerMemberRepository ledgerMemberRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.ledgerMemberRepository = ledgerMemberRepository;
    }

    /**
     * 获取账本所有账户（含实时计算的当前余额与交易笔数），在用账户优先。
     *
     * @param username 当前登录用户——「默认账户」是个人偏好，同一账本对不同用户结果可能不同
     */
    @Transactional(readOnly = true)
    public List<AccountDto> getLedgerAccounts(Long ledgerId, String username) {
        logger.debug("获取账本 {} 的账户列表", ledgerId);

        List<Account> accounts = accountRepository.findByLedgerIdOrderByActiveDescIdAsc(ledgerId);
        if (accounts.isEmpty()) {
            return new ArrayList<>();
        }

        // 一次取各账户收支净额与笔数，避免逐账户 N+1
        // 说明：TRANSFER 行按 account_id（转出方）分组，signedAmount 对其取负 = 转出；
        //      转入方（counter_account_id）的入账单独由 sumTransferInByAccount 累加。
        Map<Long, BigDecimal> netByAccount = new HashMap<>();
        Map<Long, Long> countByAccount = new HashMap<>();
        for (Object[] row : transactionRepository.sumAmountByAccountAndType(ledgerId)) {
            Long accountId = ((Number) row[0]).longValue();
            TransactionType type = (TransactionType) row[1];
            BigDecimal signed = signedAmount(type, NumberUtils.toBigDecimal(row[2]));
            long count = ((Number) row[3]).longValue();
            netByAccount.merge(accountId, signed, BigDecimal::add);
            countByAccount.merge(accountId, count, Long::sum);
        }
        // 转入方：转账金额计入转入账户余额（为正）
        for (Object[] row : transactionRepository.sumTransferInByAccount(ledgerId)) {
            Long accountId = ((Number) row[0]).longValue();
            BigDecimal in = NumberUtils.toBigDecimal(row[1]);
            long count = ((Number) row[2]).longValue();
            netByAccount.merge(accountId, in, BigDecimal::add);
            countByAccount.merge(accountId, count, Long::sum);
        }

        Long defaultAccountId = ledgerMemberRepository.findByLedgerIdAndUsername(ledgerId, username)
                .map(LedgerMember::getDefaultAccountId)
                .orElse(null);

        List<AccountDto> result = new ArrayList<>();
        for (Account account : accounts) {
            BigDecimal net = netByAccount.getOrDefault(account.getId(), BigDecimal.ZERO);
            long count = countByAccount.getOrDefault(account.getId(), 0L);
            result.add(convertToDto(account, account.getInitialBalance().add(net), count,
                    account.getId().equals(defaultAccountId)));
        }
        return result;
    }

    /**
     * 分析 prompt 用的轻量账户选项（id+name），不计算余额。
     */
    @Transactional(readOnly = true)
    public List<AnalysisNamedOption> getLedgerAccountOptions(Long ledgerId) {
        return accountRepository.findAnalysisOptionsByLedgerId(ledgerId, PageRequest.of(0, 30));
    }

    /**
     * 创建账户（归属当前账本，成员共享）
     */
    @Transactional
    public AccountDto createAccount(Long ledgerId, String username, CreateAccountRequest request) {
        String name = request.getName() == null ? "" : request.getName().trim();
        if (name.isEmpty()) {
            throw new BusinessException("账户名称不能为空");
        }
        if (request.getType() == null) {
            throw new BusinessException("账户类型不能为空");
        }
        if (accountRepository.existsByNameAndLedgerId(name, ledgerId)) {
            throw new BusinessException("账户已存在: " + name);
        }

        Account account = new Account();
        account.setName(name);
        account.setType(request.getType());
        account.setInitialBalance(request.getInitialBalance() == null ? BigDecimal.ZERO : request.getInitialBalance());
        account.setIcon(request.getIcon());
        account.setColor(request.getColor());
        account.setActive(true);
        account.setLedgerId(ledgerId);
        account.setCreatedBy(username);

        Account saved = accountRepository.save(account);
        logger.info("账本 {} 创建账户成功: {}", ledgerId, saved.getName());
        // 新账户暂无交易，当前余额 = 期初余额；且不可能是任何人的默认账户
        return convertToDto(saved, saved.getInitialBalance(), 0L, false);
    }

    /**
     * 更新账户（局部更新，仅改非 null 字段）
     */
    @Transactional
    public AccountDto updateAccount(Long ledgerId, String username, Long accountId, UpdateAccountRequest request) {
        Account account = requireAccountInLedger(ledgerId, accountId);

        if (request.getName() != null) {
            String newName = request.getName().trim();
            if (!newName.isEmpty() && !newName.equals(account.getName())) {
                if (accountRepository.existsByNameAndLedgerId(newName, ledgerId)) {
                    throw new BusinessException("账户名称已存在: " + newName);
                }
                account.setName(newName);
            }
        }
        if (request.getType() != null) {
            account.setType(request.getType());
        }
        if (request.getInitialBalance() != null) {
            account.setInitialBalance(request.getInitialBalance());
        }
        if (request.getIcon() != null) {
            account.setIcon(request.getIcon());
        }
        if (request.getColor() != null) {
            account.setColor(request.getColor());
        }
        if (request.getActive() != null) {
            account.setActive(request.getActive());
        }

        Account saved = accountRepository.save(account);
        if (!saved.isActive()) {
            // 停用的账户不会出现在任何选择器里，保留"默认账户"指向等于留了个不生效的设置
            ledgerMemberRepository.clearDefaultAccount(ledgerId, accountId);
        }
        logger.info("账本 {} 更新账户成功: {}", ledgerId, saved.getName());
        return toDtoWithBalance(saved, username);
    }

    /**
     * 删除账户：有关联交易则阻止（提示改用停用归档）；无引用才硬删。
     */
    @Transactional
    public void deleteAccount(Long ledgerId, Long accountId) {
        Account account = requireAccountInLedger(ledgerId, accountId);

        long count = transactionRepository.countByLedgerIdAndAccountId(ledgerId, accountId)
                + transactionRepository.countByLedgerIdAndCounterAccountId(ledgerId, accountId);
        if (count > 0) {
            throw new BusinessException("该账户下仍有 " + count + " 笔交易/转账，无法删除；可改为「停用归档」或先解除这些交易的账户关联");
        }

        accountRepository.delete(account);
        ledgerMemberRepository.clearDefaultAccount(ledgerId, accountId);
        logger.info("账本 {} 删除账户成功: {}", ledgerId, account.getName());
    }

    /**
     * 设置当前用户在本账本的默认账户（新建交易时前端自动选中）。
     *
     * @param accountId 账户 id；传 {@code null} 表示清除该偏好
     */
    @Transactional
    public void setDefaultAccount(Long ledgerId, String username, Long accountId) {
        if (accountId != null) {
            Account account = requireAccountInLedger(ledgerId, accountId);
            if (!account.isActive()) {
                throw new BusinessException("停用的账户不能设为默认账户");
            }
        }
        LedgerMember member = requireMembership(ledgerId, username);
        member.setDefaultAccountId(accountId);
        ledgerMemberRepository.save(member);
        logger.info("用户 {} 在账本 {} 设置默认账户为 {}", username, ledgerId, accountId);
    }

    /** 取当前用户在本账本的成员行；账本作用域接口由拦截器保证成员身份，缺失属异常状态。 */
    private LedgerMember requireMembership(Long ledgerId, String username) {
        return ledgerMemberRepository.findByLedgerIdAndUsername(ledgerId, username)
                .orElseThrow(() -> new ResourceNotFoundException("当前用户不属于该账本"));
    }

    /**
     * 校验账户存在且属于指定账本；否则抛 {@link ResourceNotFoundException}（→404，
     * 「不存在」与「不属于当前账本」统一按资源未找到处理，不泄露他人账本资源的存在性）。
     * 供本服务写操作与交易写入路径在关联账户前调用。
     */
    @Transactional(readOnly = true)
    public Account requireAccountInLedger(Long ledgerId, Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("账户不存在: " + accountId));
        if (!ledgerId.equals(account.getLedgerId())) {
            throw new ResourceNotFoundException("账户不属于当前账本");
        }
        return account;
    }

    /**
     * 按名称获取或创建账户（CSV 导入用，归属当前账本）。未命中按 OTHER 类型创建，
     * 以保证「导出 → 修改 → 再导入」可往返。
     */
    @Transactional
    public Account getOrCreateAccount(Long ledgerId, String username, String accountName) {
        String name = accountName.trim();
        if (name.isEmpty()) {
            throw new BusinessException("账户名称不能为空");
        }
        return accountRepository.findByNameAndLedgerId(name, ledgerId)
                .orElseGet(() -> {
                    Account account = new Account();
                    account.setName(name);
                    account.setType(AccountType.OTHER);
                    account.setInitialBalance(BigDecimal.ZERO);
                    account.setActive(true);
                    account.setLedgerId(ledgerId);
                    account.setCreatedBy(username);
                    Account saved = accountRepository.save(account);
                    logger.debug("自动创建账户: {} (账本: {})", name, ledgerId);
                    return saved;
                });
    }

    /**
     * 账本内账户 id → 名称映射（CSV 导出按 accountId 取名用）
     */
    @Transactional(readOnly = true)
    public Map<Long, String> getAccountNameMap(Long ledgerId) {
        Map<Long, String> map = new HashMap<>();
        for (Account account : accountRepository.findByLedgerIdOrderByActiveDescIdAsc(ledgerId)) {
            map.put(account.getId(), account.getName());
        }
        return map;
    }

    /** 单账户重新计算当前余额与笔数后转 DTO */
    private AccountDto toDtoWithBalance(Account account, String username) {
        BigDecimal net = BigDecimal.ZERO;
        long count = 0L;
        for (Object[] row : transactionRepository.sumAmountByAccountGroupByType(account.getId())) {
            TransactionType type = (TransactionType) row[0];
            net = net.add(signedAmount(type, NumberUtils.toBigDecimal(row[1])));
            count += ((Number) row[2]).longValue();
        }
        // 作为转入方的转账（为正）
        for (Object[] row : transactionRepository.sumTransferInByAccountSingle(account.getId())) {
            net = net.add(NumberUtils.toBigDecimal(row[0]));
            count += ((Number) row[1]).longValue();
        }
        return convertToDto(account, account.getInitialBalance().add(net), count, isDefaultAccount(account, username));
    }

    /** 该账户是否为指定用户在其所属账本的默认账户 */
    private boolean isDefaultAccount(Account account, String username) {
        return ledgerMemberRepository.findByLedgerIdAndUsername(account.getLedgerId(), username)
                .map(member -> account.getId().equals(member.getDefaultAccountId()))
                .orElse(false);
    }

    private BigDecimal signedAmount(TransactionType type, BigDecimal amount) {
        return type == TransactionType.INCOME ? amount : amount.negate();
    }

    private AccountDto convertToDto(Account account, BigDecimal currentBalance, long transactionCount,
                                    boolean isDefault) {
        AccountDto dto = new AccountDto();
        dto.setId(account.getId());
        dto.setName(account.getName());
        dto.setType(account.getType());
        dto.setInitialBalance(account.getInitialBalance());
        dto.setCurrentBalance(currentBalance);
        dto.setIcon(account.getIcon());
        dto.setColor(account.getColor());
        dto.setActive(account.isActive());
        dto.setDefault(isDefault);
        dto.setTransactionCount(transactionCount);
        dto.setCreatedAt(account.getCreatedAt());
        return dto;
    }

    // toBigDecimal / toLong 统一使用 util.NumberUtils
}
