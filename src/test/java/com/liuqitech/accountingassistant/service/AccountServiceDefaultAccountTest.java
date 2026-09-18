package com.liuqitech.accountingassistant.service;

import com.liuqitech.accountingassistant.dto.AccountDto;
import com.liuqitech.accountingassistant.entity.Account;
import com.liuqitech.accountingassistant.entity.LedgerMember;
import com.liuqitech.accountingassistant.enums.AccountType;
import com.liuqitech.accountingassistant.enums.LedgerRole;
import com.liuqitech.accountingassistant.exception.BusinessException;
import com.liuqitech.accountingassistant.exception.ResourceNotFoundException;
import com.liuqitech.accountingassistant.dto.UpdateAccountRequest;
import com.liuqitech.accountingassistant.repository.AccountRepository;
import com.liuqitech.accountingassistant.repository.LedgerMemberRepository;
import com.liuqitech.accountingassistant.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 「默认账户」是每个成员在每个账本各自一份的个人偏好，存在 ledger_members 行上。
 * 本测试覆盖设置/清除、校验，以及默认账户被停用或删除后的清理。
 */
@ExtendWith(MockitoExtension.class)
class AccountServiceDefaultAccountTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private LedgerMemberRepository ledgerMemberRepository;

    private AccountService accountService;

    private AccountService service() {
        if (accountService == null) {
            accountService = new AccountService(accountRepository, transactionRepository, ledgerMemberRepository);
        }
        return accountService;
    }

    @Test
    void setDefaultAccountPersistsOnTheMembersOwnRow() {
        LedgerMember member = new LedgerMember(1L, "alice", LedgerRole.OWNER);
        when(accountRepository.findById(7L)).thenReturn(Optional.of(account(7L, 1L, true)));
        when(ledgerMemberRepository.findByLedgerIdAndUsername(1L, "alice")).thenReturn(Optional.of(member));

        service().setDefaultAccount(1L, "alice", 7L);

        assertThat(member.getDefaultAccountId()).isEqualTo(7L);
        verify(ledgerMemberRepository).save(member);
    }

    @Test
    void setDefaultAccountWithNullClearsThePreference() {
        LedgerMember member = new LedgerMember(1L, "alice", LedgerRole.OWNER);
        member.setDefaultAccountId(7L);
        when(ledgerMemberRepository.findByLedgerIdAndUsername(1L, "alice")).thenReturn(Optional.of(member));

        service().setDefaultAccount(1L, "alice", null);

        assertThat(member.getDefaultAccountId()).isNull();
        verify(ledgerMemberRepository).save(member);
    }

    @Test
    void setDefaultAccountRejectsAccountFromAnotherLedger() {
        when(accountRepository.findById(7L)).thenReturn(Optional.of(account(7L, 2L, true)));

        assertThatThrownBy(() -> service().setDefaultAccount(1L, "alice", 7L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(ledgerMemberRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void setDefaultAccountRejectsInactiveAccount() {
        when(accountRepository.findById(7L)).thenReturn(Optional.of(account(7L, 1L, false)));

        assertThatThrownBy(() -> service().setDefaultAccount(1L, "alice", 7L))
                .isInstanceOf(BusinessException.class);
        verify(ledgerMemberRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void ledgerAccountsMarkOnlyTheCurrentMembersDefault() {
        when(accountRepository.findByLedgerIdOrderByActiveDescIdAsc(1L))
                .thenReturn(List.of(account(7L, 1L, true), account(8L, 1L, true)));
        LedgerMember member = new LedgerMember(1L, "alice", LedgerRole.OWNER);
        member.setDefaultAccountId(8L);
        when(ledgerMemberRepository.findByLedgerIdAndUsername(1L, "alice")).thenReturn(Optional.of(member));

        List<AccountDto> accounts = service().getLedgerAccounts(1L, "alice");

        assertThat(accounts).filteredOn(AccountDto::isDefault).extracting(AccountDto::getId)
                .containsExactly(8L);
    }

    @Test
    void ledgerAccountsDoNotMarkAnyDefaultWhenTheMemberHasNone() {
        when(accountRepository.findByLedgerIdOrderByActiveDescIdAsc(1L))
                .thenReturn(List.of(account(7L, 1L, true)));
        when(ledgerMemberRepository.findByLedgerIdAndUsername(1L, "alice"))
                .thenReturn(Optional.of(new LedgerMember(1L, "alice", LedgerRole.OWNER)));

        List<AccountDto> accounts = service().getLedgerAccounts(1L, "alice");

        assertThat(accounts).noneMatch(AccountDto::isDefault);
    }

    @Test
    void deactivatingAnAccountClearsEveryMembersDefaultPointingAtIt() {
        Account account = account(7L, 1L, true);
        when(accountRepository.findById(7L)).thenReturn(Optional.of(account));
        when(accountRepository.save(account)).thenReturn(account);
        UpdateAccountRequest request = new UpdateAccountRequest();
        request.setActive(false);

        service().updateAccount(1L, "alice", 7L, request);

        verify(ledgerMemberRepository).clearDefaultAccount(1L, 7L);
    }

    @Test
    void reactivatingAnAccountLeavesOtherDefaultsAlone() {
        Account account = account(7L, 1L, false);
        when(accountRepository.findById(7L)).thenReturn(Optional.of(account));
        when(accountRepository.save(account)).thenReturn(account);
        UpdateAccountRequest request = new UpdateAccountRequest();
        request.setActive(true);

        service().updateAccount(1L, "alice", 7L, request);

        verify(ledgerMemberRepository, never()).clearDefaultAccount(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deletingAnAccountClearsEveryMembersDefaultPointingAtIt() {
        Account account = account(7L, 1L, true);
        when(accountRepository.findById(7L)).thenReturn(Optional.of(account));

        service().deleteAccount(1L, 7L);

        verify(ledgerMemberRepository).clearDefaultAccount(1L, 7L);
        verify(accountRepository).delete(account);
    }

    private Account account(Long id, Long ledgerId, boolean active) {
        Account account = new Account();
        account.setId(id);
        account.setName("账户" + id);
        account.setType(AccountType.CASH);
        account.setInitialBalance(BigDecimal.ZERO);
        account.setActive(active);
        account.setLedgerId(ledgerId);
        return account;
    }
}
