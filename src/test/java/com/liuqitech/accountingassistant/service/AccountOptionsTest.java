package com.liuqitech.accountingassistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liuqitech.accountingassistant.entity.Account;
import com.liuqitech.accountingassistant.entity.LedgerMember;
import com.liuqitech.accountingassistant.enums.AccountType;
import com.liuqitech.accountingassistant.enums.LedgerRole;
import com.liuqitech.accountingassistant.repository.AccountRepository;
import com.liuqitech.accountingassistant.repository.LedgerMemberRepository;
import com.liuqitech.accountingassistant.repository.TransactionRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AccountOptionsTest {
    @Test
    void optionsDoNotQueryTransactionsAndExposeTheExistingDefaultField() {
        AccountRepository accounts = mock(AccountRepository.class);
        TransactionRepository transactions = mock(TransactionRepository.class);
        LedgerMemberRepository members = mock(LedgerMemberRepository.class);
        AccountService service = new AccountService(accounts, transactions, members);
        Account account = new Account();
        account.setId(7L);
        account.setName("现金");
        account.setType(AccountType.CASH);
        account.setActive(true);
        LedgerMember member = new LedgerMember(3L, "owner", LedgerRole.OWNER);
        member.setDefaultAccountId(7L);
        when(accounts.findByLedgerIdOrderByActiveDescIdAsc(3L)).thenReturn(List.of(account));
        when(members.findByLedgerIdAndUsername(3L, "owner")).thenReturn(Optional.of(member));

        var options = service.getAccountOptions(3L, "owner");
        var json = new ObjectMapper().valueToTree(options.get(0));
        assertThat(json.get("default").asBoolean()).isTrue();
        assertThat(json.has("isDefault")).isFalse();
        assertThat(json.has("currentBalance")).isFalse();
        verifyNoInteractions(transactions);
    }
}
