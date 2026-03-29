package unical.it.webapp.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import unical.it.webapp.service.AccountService;

/** Cancella l'account con cui sei loggato. */
@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @DeleteMapping
    @PreAuthorize("hasAnyRole('ACQUIRENTE','VENDITORE')")
    public ResponseEntity<Void> deleteAccount() {
        accountService.deleteCurrentUserAccount();
        return ResponseEntity.noContent().build();
    }
}
