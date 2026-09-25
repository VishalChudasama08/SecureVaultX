package com.securevaultx.backend.controllers;

import com.securevaultx.backend.response.VaultFileResponse;
import com.securevaultx.backend.security.AppUserDetails;
import com.securevaultx.backend.services.VaultService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Mode A endpoints. The owner always comes from the session principal, never from the request. */
@RestController
@RequestMapping("/api/vault/files")
public class VaultController {

	private final VaultService vault;

	public VaultController(VaultService vault) {
		this.vault = vault;
	}

	/** Store in Secure Vault: body = plaintext file (max size is a server policy), header X-Filename. */
	@PostMapping(consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
	public ResponseEntity<VaultFileResponse> store(@AuthenticationPrincipal AppUserDetails user,
			@RequestHeader(value = FilenameHeader.NAME, required = false) String filename,
			HttpServletRequest request) throws IOException {
		VaultFileResponse created = vault.store(user.getId(), FilenameHeader.decode(filename),
				request.getInputStream(), request.getContentLengthLong());
		return ResponseEntity.created(URI.create("/api/vault/files/" + created.id())).body(created);
	}

	@GetMapping
	public List<VaultFileResponse> list(@AuthenticationPrincipal AppUserDetails user) {
		return vault.list(user.getId());
	}

	@GetMapping("/{id}")
	public VaultFileResponse get(@AuthenticationPrincipal AppUserDetails user, @PathVariable UUID id) {
		return vault.get(user.getId(), id);
	}

	/** Download the stored .enc file (still encrypted). */
	@GetMapping("/{id}/encrypted")
	public void downloadEncrypted(@AuthenticationPrincipal AppUserDetails user, @PathVariable UUID id,
			HttpServletResponse response) throws IOException {
		StreamedDownloadWriter.write(response, vault.encryptedDownload(user.getId(), id));
	}

	/** Decrypt on the server and download the original file (only after full authentication succeeds). */
	@GetMapping("/{id}/content")
	public void downloadDecrypted(@AuthenticationPrincipal AppUserDetails user, @PathVariable UUID id,
			HttpServletResponse response) throws IOException {
		StreamedDownloadWriter.write(response, vault.decryptedDownload(user.getId(), id));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@AuthenticationPrincipal AppUserDetails user, @PathVariable UUID id) {
		vault.delete(user.getId(), id);
		return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
	}
}
