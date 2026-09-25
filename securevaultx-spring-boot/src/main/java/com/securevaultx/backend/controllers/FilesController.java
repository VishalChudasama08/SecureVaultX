package com.securevaultx.backend.controllers;

import com.securevaultx.backend.config.StorageProperties;
import com.securevaultx.backend.response.PolicyResponse;
import com.securevaultx.backend.security.AppUserDetails;
import com.securevaultx.backend.services.TransferService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mode B endpoints. Request bodies are the raw file bytes (application/octet-stream), not multipart, so nothing is
 * buffered or spooled in plaintext by the servlet container: the stream goes straight into the cipher.
 */
@RestController
@RequestMapping("/api/files")
public class FilesController {

	private final TransferService transfer;
	private final StorageProperties props;

	public FilesController(TransferService transfer, StorageProperties props) {
		this.transfer = transfer;
		this.props = props;
	}

	@GetMapping("/policy")
	public PolicyResponse policy() {
		return new PolicyResponse(props.maxVaultFileSize().toBytes(), props.maxUploadSize().toBytes());
	}

	/** Encrypt &amp; Download: body = plaintext file, response = the .enc file. Content-Length is required. */
	@PostMapping(value = "/encrypt", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
	public void encrypt(@AuthenticationPrincipal AppUserDetails user,
			@RequestHeader(value = FilenameHeader.NAME, required = false) String filename,
			HttpServletRequest request, HttpServletResponse response) throws IOException {
		StreamedDownloadWriter.write(response, transfer.encryptForDownload(user.getId(),
				FilenameHeader.decode(filename), request.getContentLengthLong(), request.getInputStream()));
	}

	/** Decrypt: body = a .enc file previously produced for this account, response = the original file. */
	@PostMapping(value = "/decrypt", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
	public void decrypt(@AuthenticationPrincipal AppUserDetails user,
			HttpServletRequest request, HttpServletResponse response) throws IOException {
		StreamedDownloadWriter.write(response, transfer.decryptUpload(user.getId(), request.getInputStream(),
				request.getContentLengthLong()));
	}
}
