package com.wangyue.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wangyue.backend.dto.CreateQuestionOptionRequest;
import com.wangyue.backend.dto.CreateQuestionRequest;
import com.wangyue.backend.dto.ApiErrorResponse;
import com.wangyue.backend.dto.RegisterRequest;
import com.wangyue.backend.dto.ImportFileResponse;
import com.wangyue.backend.dto.BatchDraftConfirmResponse;
import com.wangyue.backend.dto.RecognizedQuestion;
import com.wangyue.backend.dto.RecognizedQuestionOption;
import com.wangyue.backend.dto.SubmitAnswerRequest;
import com.wangyue.backend.dto.SubmitAnswerResponse;
import com.wangyue.backend.exception.AuthenticationException;
import com.wangyue.backend.entity.AnswerRecord;
import com.wangyue.backend.entity.AppUser;
import com.wangyue.backend.entity.LearningLibrary;
import com.wangyue.backend.entity.ImportFile;
import com.wangyue.backend.entity.ImportBatch;
import com.wangyue.backend.entity.Question;
import com.wangyue.backend.entity.QuestionDraft;
import com.wangyue.backend.entity.WrongQuestion;
import com.wangyue.backend.mapper.AnswerRecordMapper;
import com.wangyue.backend.mapper.AppUserMapper;
import com.wangyue.backend.mapper.LearningLibraryMapper;
import com.wangyue.backend.mapper.ImportFileMapper;
import com.wangyue.backend.mapper.ImportBatchMapper;
import com.wangyue.backend.mapper.QuestionMapper;
import com.wangyue.backend.mapper.QuestionDraftMapper;
import com.wangyue.backend.mapper.WrongQuestionMapper;
import com.wangyue.backend.service.LearningLibraryService;
import com.wangyue.backend.service.AuthService;
import com.wangyue.backend.service.QuestionService;
import com.wangyue.backend.service.PracticeService;
import com.wangyue.backend.service.QuestionDraftService;
import com.wangyue.backend.service.OcrService;
import com.wangyue.backend.service.ImportService;
import com.wangyue.backend.service.LlmService;
import com.wangyue.backend.service.WordDocumentService;
import com.wangyue.backend.service.JwtTokenService;
import com.wangyue.backend.controller.ApiExceptionHandler;
import com.wangyue.backend.exception.OperationConflictException;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.OutputStream;
import javax.imageio.ImageIO;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.import.processing.enabled=false")
@AutoConfigureMockMvc
class BackendApplicationTests {

	@Test
	void globalExceptionHandlerOnlyExposesSafeMessagesToUsers() {
		ApiExceptionHandler handler = new ApiExceptionHandler();

		ApiErrorResponse authenticationResponse = handler.handleAuthenticationFailure(
			new AuthenticationException()
		);
		assertEquals("用户名或密码错误", authenticationResponse.getMessage());
		assertEquals("下一步：请检查用户名和密码后重新登录。", authenticationResponse.getAction());

		ApiErrorResponse conflictResponse = handler.handleOperationConflict(
			new OperationConflictException("草稿状态已变化，请刷新后重试")
		);
		assertEquals("草稿状态已变化，请刷新后重试", conflictResponse.getMessage());
		assertEquals("下一步：请刷新页面后重试。", conflictResponse.getAction());

		ApiErrorResponse unexpectedResponse = handler.handleUnexpectedException(
			new IllegalStateException("database password must not reach the app"),
			new MockHttpServletRequest("POST", "/api/example")
		);
		assertEquals("服务暂时异常，请稍后重试", unexpectedResponse.getMessage());
		assertEquals("下一步：请稍后再次尝试；若持续出现，请联系管理员。", unexpectedResponse.getAction());
	}

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private LearningLibraryMapper learningLibraryMapper;

	@Autowired
	private LearningLibraryService learningLibraryService;

	@Autowired
	private QuestionService questionService;

	@Autowired
	private AnswerRecordMapper answerRecordMapper;

	@Autowired
	private QuestionMapper questionMapper;

	@Autowired
	private WrongQuestionMapper wrongQuestionMapper;

	@Autowired
	private PracticeService practiceService;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private AppUserMapper appUserMapper;

	@Autowired
	private AuthService authService;

	@Autowired
	private ImportFileMapper importFileMapper;

	@Autowired
	private ImportBatchMapper importBatchMapper;

	@Autowired
	private QuestionDraftMapper questionDraftMapper;

	@Autowired
	private QuestionDraftService questionDraftService;

	@Autowired
	private ImportService importService;

	@Autowired
	private OcrService ocrService;

	@Autowired
	private LlmService llmService;

	@Autowired
	private WordDocumentService wordDocumentService;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenService jwtTokenService;

	private AppUser requestUser;
	private String requestUserAccessToken;

	@BeforeEach
	void createRequestUser() {
		requestUser = new AppUser();
		requestUser.setUsername("request-user-" + System.nanoTime());
		requestUser.setPasswordHash(passwordEncoder.encode("demo-password-123"));
		appUserMapper.insert(requestUser);
		requestUserAccessToken = jwtTokenService.createAccessToken(requestUser.getId());
	}

	@AfterEach
	void deleteRequestUser() {
		if (requestUser != null && requestUser.getId() != null) {
			appUserMapper.deleteById(requestUser.getId());
		}
	}

	@Test
	void llmServiceTurnsRecognizedTextIntoQuestionObjects() {
		List<RecognizedQuestion> questions = llmService.structureQuestions("""
			1. 1 + 1 等于几？
			A. 1
			B. 2
			答案：B
			""");

		assertFalse(questions.isEmpty());
		assertFalse(questions.get(0).getStem().isBlank());
		assertFalse(questions.get(0).getOptions().isEmpty());
	}

	@Test
	@EnabledIfSystemProperty(named = "includeOcrIntegrationTests", matches = "true")
	void ocrServiceCanReadTextFromATemporaryImage() throws Exception {
		Path imagePath = Files.createTempFile("ocr-service-test-", ".png");
		try {
			BufferedImage image = new BufferedImage(900, 260, BufferedImage.TYPE_INT_RGB);
			Graphics2D graphics = image.createGraphics();
			graphics.setColor(Color.WHITE);
			graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
			graphics.setColor(Color.BLACK);
			graphics.setFont(new Font("Arial", Font.BOLD, 72));
			graphics.drawString("PADDLE OCR TEST", 40, 150);
			graphics.dispose();
			ImageIO.write(image, "png", imagePath.toFile());

			String recognizedText = ocrService.recognizeImage(imagePath);
			assertTrue(!recognizedText.isBlank());
		} finally {
			Files.deleteIfExists(imagePath);
		}
	}

	@Test
	void wordDocumentServiceReadsTextFromATemporaryDocx() throws Exception {
		Path documentPath = Files.createTempFile("word-document-service-test-", ".docx");
		try {
			try (
				XWPFDocument document = new XWPFDocument();
				OutputStream outputStream = Files.newOutputStream(documentPath)
			) {
				document.createParagraph().createRun().setText("1. 进程调度的作用是？");
				document.createParagraph().createRun().setText("A. 选择下一个运行进程");
				document.write(outputStream);
			}

			String text = wordDocumentService.extractText(documentPath);
			assertTrue(text.contains("进程调度的作用是？"));
			assertTrue(text.contains("选择下一个运行进程"));
		} finally {
			Files.deleteIfExists(documentPath);
		}
	}

	@Test
	void wordTextIsSplitAtQuestionBoundariesForIncrementalGeneration() {
		WordDocumentService.QuestionTextChunks chunks = wordDocumentService.splitQuestionText("""
			单选题
			1. 第一题？
			A. 选项一
			2．第二题？
			A. 选项二
			3、第三题？
			A. 选项三
			4. 第四题？
			A. 选项四
			5. 第五题？
			A. 选项五
			""", 2);

		assertEquals(5, chunks.estimatedQuestionCount());
		assertEquals(3, chunks.chunks().size());
		assertTrue(chunks.chunks().get(0).contains("1. 第一题"));
		assertTrue(chunks.chunks().get(0).contains("2．第二题"));
		assertTrue(chunks.chunks().get(1).contains("3、第三题"));
		assertTrue(chunks.chunks().get(2).contains("5. 第五题"));
	}

	@Test
	void wordTextStartsANewChunkWhenThePreviousQuestionTextIsTooLong() {
		WordDocumentService.QuestionTextChunks chunks = wordDocumentService.splitQuestionText("""
			1. 第一题的题干很长很长很长很长很长很长
			A. 选项一
			2. 第二题？
			A. 选项二
			""", 3, 30);

		assertEquals(2, chunks.chunks().size());
		assertTrue(chunks.chunks().get(0).contains("第一题"));
		assertTrue(chunks.chunks().get(1).contains("第二题"));
	}

	@Test
	void structuredWordTextIsParsedLocallyWithoutCallingTheLlm() {
		WordDocumentService.ParsedQuestions parsed = wordDocumentService.parseStructuredQuestions("""
			第一部分 单项选择题
			1. 进程调度的作用是？
			A.管理文件 B.选择下一个运行进程 C.分配内存 D.删除文件
			【答案】B
			【解析】由调度程序选择。
			第二部分 多项选择题
			2．下列哪些属于存储设备？
			A．硬盘 B．内存 C．键盘
			【答案】A、B
			""");

		assertEquals(2, parsed.estimatedQuestionCount());
		assertEquals("SINGLE_CHOICE", parsed.questions().get(0).getQuestionType());
		assertEquals("进程调度的作用是？", parsed.questions().get(0).getStem());
		assertEquals(4, parsed.questions().get(0).getOptions().size());
		assertEquals("选择下一个运行进程", parsed.questions().get(0).getOptions().get(1).getContent());
		assertEquals(List.of("B"), parsed.questions().get(0).getCorrectAnswer());
		assertEquals("MULTIPLE_CHOICE", parsed.questions().get(1).getQuestionType());
		assertEquals(List.of("A", "B"), parsed.questions().get(1).getCorrectAnswer());
	}

	@Test
	void wordRecognitionChangesOnlyItsOwnFileStatus() throws Exception {
		LearningLibrary library = createLibraryForRequestUser("word-flow-test-" + System.nanoTime());
		Path documentPath = Files.createTempFile("word-import-test-", ".docx");
		ImportBatch batch = new ImportBatch();
		batch.setLibraryId(library.getId());
		batch.setStatus("WAITING_RECOGNITION");
		importBatchMapper.insert(batch);

		ImportFile wordFile = new ImportFile();
		wordFile.setImportBatchId(batch.getId());
		wordFile.setOriginalFileName("questions.docx");
		wordFile.setStoredFilePath(documentPath.toString());
		wordFile.setFileType("application/octet-stream");
		wordFile.setFileSizeBytes(1L);
		wordFile.setStatus("WAITING_RECOGNITION");

		ImportFile unrelatedFile = new ImportFile();
		unrelatedFile.setImportBatchId(batch.getId());
		unrelatedFile.setOriginalFileName("still-waiting.png");
		unrelatedFile.setStoredFilePath(documentPath.toString());
		unrelatedFile.setFileType("image/png");
		unrelatedFile.setFileSizeBytes(1L);
		unrelatedFile.setStatus("WAITING_RECOGNITION");

		try {
			try (
				XWPFDocument document = new XWPFDocument();
				OutputStream outputStream = Files.newOutputStream(documentPath)
			) {
				document.createParagraph().createRun().setText("1. 进程调度的作用是？");
				document.createParagraph().createRun().setText("A. 选择下一个运行进程");
				document.write(outputStream);
			}
			importFileMapper.insert(wordFile);
			importFileMapper.insert(unrelatedFile);

			ImportFileResponse result = importService.recognizeFile(library.getId(), wordFile.getId());
			assertEquals("WAITING_STRUCTURING", result.getStatus());
			assertTrue(importFileMapper.selectById(wordFile.getId()).getRecognitionText().contains("进程调度"));
			assertEquals("WAITING_RECOGNITION", importFileMapper.selectById(unrelatedFile.getId()).getStatus());
		} finally {
			Files.deleteIfExists(documentPath);
			importFileMapper.deleteById(wordFile.getId());
			importFileMapper.deleteById(unrelatedFile.getId());
			importBatchMapper.deleteById(batch.getId());
			learningLibraryMapper.deleteById(library.getId());
		}
	}

	@Test
	@EnabledIfSystemProperty(named = "includeOcrIntegrationTests", matches = "true")
	void imageRecognitionChangesOnlyItsOwnFileStatus() throws Exception {
		LearningLibrary library = createLibraryForRequestUser("ocr-flow-test-" + System.nanoTime());
		Path imagePath = Files.createTempFile("ocr-import-test-", ".png");
		ImportBatch batch = new ImportBatch();
		batch.setLibraryId(library.getId());
		batch.setStatus("WAITING_RECOGNITION");
		importBatchMapper.insert(batch);

		ImportFile successfulFile = new ImportFile();
		successfulFile.setImportBatchId(batch.getId());
		successfulFile.setOriginalFileName("ocr-success.png");
		successfulFile.setStoredFilePath(imagePath.toString());
		successfulFile.setFileType("image/png");
		successfulFile.setFileSizeBytes(1L);
		successfulFile.setStatus("WAITING_RECOGNITION");

		ImportFile unrelatedFile = new ImportFile();
		unrelatedFile.setImportBatchId(batch.getId());
		unrelatedFile.setOriginalFileName("still-waiting.png");
		unrelatedFile.setStoredFilePath(imagePath.toString());
		unrelatedFile.setFileType("image/png");
		unrelatedFile.setFileSizeBytes(1L);
		unrelatedFile.setStatus("WAITING_RECOGNITION");

		try {
			BufferedImage image = new BufferedImage(900, 260, BufferedImage.TYPE_INT_RGB);
			Graphics2D graphics = image.createGraphics();
			graphics.setColor(Color.WHITE);
			graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
			graphics.setColor(Color.BLACK);
			graphics.setFont(new Font("Arial", Font.BOLD, 72));
			graphics.drawString("PADDLE OCR TEST", 40, 150);
			graphics.dispose();
			ImageIO.write(image, "png", imagePath.toFile());
			importFileMapper.insert(successfulFile);
			importFileMapper.insert(unrelatedFile);

			ImportFileResponse result = importService.recognizeFile(library.getId(), successfulFile.getId());
			assertEquals("WAITING_STRUCTURING", result.getStatus());
			assertTrue(!importFileMapper.selectById(successfulFile.getId()).getRecognitionText().isBlank());
			assertEquals("WAITING_RECOGNITION", importFileMapper.selectById(unrelatedFile.getId()).getStatus());
		} finally {
			Files.deleteIfExists(imagePath);
			importFileMapper.deleteById(successfulFile.getId());
			importFileMapper.deleteById(unrelatedFile.getId());
			importBatchMapper.deleteById(batch.getId());
			learningLibraryMapper.deleteById(library.getId());
		}
	}

	@Test
	void recognitionApiRejectsAFileFromAnotherLibrary() throws Exception {
		LearningLibrary ownerLibrary = createLibraryForRequestUser("recognize-owner-" + System.nanoTime());
		LearningLibrary otherLibrary = createLibraryForRequestUser("recognize-other-" + System.nanoTime());
		String imageFileName = "recognize-source-" + System.nanoTime() + ".png";
		try {
			mockMvc.perform(multipart("/api/libraries/" + ownerLibrary.getId() + "/import-batches")
				.file(new MockMultipartFile("files", imageFileName, "image/png", "test image".getBytes()))
				.with(authenticatedRequest()))
				.andExpect(status().isCreated());

			Long importFileId = jdbcTemplate.queryForObject(
				"SELECT id FROM import_file WHERE original_file_name = ?", Long.class, imageFileName
			);

			mockMvc.perform(post("/api/libraries/" + otherLibrary.getId()
				+ "/import-batches/files/" + importFileId + "/recognize")
				.with(authenticatedRequest()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("导入文件不属于当前学习库"));
		} finally {
			List<ImportFile> importedFiles = importFileMapper.selectList(
				new LambdaQueryWrapper<ImportFile>().eq(ImportFile::getOriginalFileName, imageFileName)
			);
			for (ImportFile importFile : importedFiles) {
				if (importFile.getStoredFilePath() != null) {
					Files.deleteIfExists(Path.of(importFile.getStoredFilePath()));
				}
			}
			learningLibraryMapper.deleteById(ownerLibrary.getId());
			learningLibraryMapper.deleteById(otherLibrary.getId());
		}
	}

	@Test
	void passwordIsHashedAndCanBeVerified() {
		String rawPassword = "demo-password-123";
		String passwordHash = passwordEncoder.encode(rawPassword);

		assertNotEquals(rawPassword, passwordHash);
		assertEquals(true, passwordEncoder.matches(rawPassword, passwordHash));
		assertEquals(false, passwordEncoder.matches("wrong-password", passwordHash));
	}

	@Test
	void appUserCanBeInsertedAndFound() {
		AppUser user = new AppUser();
		user.setUsername("app-user-mapper-test-" + System.nanoTime());
		user.setPasswordHash(passwordEncoder.encode("demo-password-123"));
		appUserMapper.insert(user);

		try {
			AppUser savedUser = appUserMapper.selectById(user.getId());
			assertNotNull(savedUser);
			assertEquals(user.getUsername(), savedUser.getUsername());
			assertNotEquals("demo-password-123", savedUser.getPasswordHash());
		} finally {
			appUserMapper.deleteById(user.getId());
		}
	}

	@Test
	void userCanRegisterWithHashedPasswordAndDuplicateUsernameIsRejected() {
		String username = "registered-user-" + System.nanoTime();
		RegisterRequest request = new RegisterRequest();
		request.setUsername("  " + username + "  ");
		request.setPassword("demo-password-123");

		AppUser user = authService.register(request);
		try {
			assertNotNull(user.getId());
			assertEquals(username, user.getUsername());
			assertEquals(true, passwordEncoder.matches("demo-password-123", user.getPasswordHash()));
			assertThrows(IllegalArgumentException.class, () -> authService.register(request));
		} finally {
			appUserMapper.deleteById(user.getId());
		}
	}

	@Test
	void userCanRegisterWithExactlyTwoCharacterUsername() {
		String username = findAvailableTwoCharacterUsername();
		RegisterRequest request = new RegisterRequest();
		request.setUsername(username);
		request.setPassword("demo-password-123");

		AppUser user = authService.register(request);
		try {
			assertEquals(2, user.getUsername().length());
		} finally {
			appUserMapper.deleteById(user.getId());
		}
	}

	@Test
	void registerApiCreatesUserAndDoesNotExposePasswordHash() throws Exception {
		String username = "register-api-" + System.nanoTime();

		mockMvc.perform(post("/api/auth/register")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"username\":\"" + username + "\",\"password\":\"demo-password-123\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.username").value(username))
			.andExpect(jsonPath("$.passwordHash").doesNotExist())
			.andExpect(jsonPath("$.password").doesNotExist());

		AppUser user = authService.findByUsername(username);
		try {
			assertNotNull(user);
			assertEquals(true, passwordEncoder.matches("demo-password-123", user.getPasswordHash()));
		} finally {
			appUserMapper.deleteById(user.getId());
		}
	}

	@Test
	void loginApiReturnsAccessTokenWithoutExposingPasswordData() throws Exception {
		String username = "login-api-" + System.nanoTime();
		String password = "demo-password-123";
		RegisterRequest registerRequest = new RegisterRequest();
		registerRequest.setUsername(username);
		registerRequest.setPassword(password);
		AppUser user = authService.register(registerRequest);

		try {
			mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.expiresInSeconds").value(7200))
				.andExpect(jsonPath("$.username").value(username))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist());
		} finally {
			appUserMapper.deleteById(user.getId());
		}
	}

	@Test
	void loginApiUsesOneGenericMessageForUnknownUserAndWrongPassword() throws Exception {
		String username = "login-failure-" + System.nanoTime();
		RegisterRequest registerRequest = new RegisterRequest();
		registerRequest.setUsername(username);
		registerRequest.setPassword("demo-password-123");
		AppUser user = authService.register(registerRequest);

		try {
			mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"missing-user\",\"password\":\"demo-password-123\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("用户名或密码错误"));

			mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"" + username + "\",\"password\":\"wrong-password\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("用户名或密码错误"));
		} finally {
			appUserMapper.deleteById(user.getId());
		}
	}

	@Test
	void loginApiRejectsMissingCredentialsAsBadRequest() throws Exception {
		mockMvc.perform(post("/api/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"username\":\"\",\"password\":\"\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("用户名和密码不能为空"));
	}

	@Test
	void protectedApiRequiresAValidJwt() throws Exception {
		mockMvc.perform(get("/api/libraries"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("请先登录或重新登录"));

		mockMvc.perform(get("/api/libraries").with(authenticatedRequest()))
			.andExpect(status().isOk());
	}

	@Test
	void protectedApiRejectsATokenWhoseUserWasDeleted() throws Exception {
		AppUser deletedUser = new AppUser();
		deletedUser.setUsername("deleted-token-user-" + System.nanoTime());
		deletedUser.setPasswordHash(passwordEncoder.encode("demo-password-123"));
		appUserMapper.insert(deletedUser);

		String deletedUserToken = jwtTokenService.createAccessToken(deletedUser.getId());
		appUserMapper.deleteById(deletedUser.getId());

		mockMvc.perform(get("/api/libraries")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + deletedUserToken))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("请先登录或重新登录"));
	}

	@Test
	void importApiKeepsOtherFilesWhenOneFileFails() throws Exception {
		LearningLibrary library = createLibraryForRequestUser("import-api-test-" + System.nanoTime());
		String imageFileName = "question-" + System.nanoTime() + ".png";
		try {
			MockMultipartFile image = new MockMultipartFile(
				"files", imageFileName, "image/png", "test image".getBytes()
			);
			MockMultipartFile unsupportedFile = new MockMultipartFile(
				"files", "unsupported.pdf", "application/pdf", "test pdf".getBytes()
			);

			mockMvc.perform(multipart("/api/libraries/" + library.getId() + "/import-batches")
				.file(image)
				.file(unsupportedFile)
				.with(authenticatedRequest()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.libraryId").value(library.getId()))
				.andExpect(jsonPath("$.status").value("PROCESSING"))
				.andExpect(jsonPath("$.files.length()").value(2))
				.andExpect(jsonPath("$.files[0].status").value("WAITING_RECOGNITION"))
				.andExpect(jsonPath("$.files[1].status").value("UPLOAD_FAILED"))
				.andExpect(jsonPath("$.files[1].errorMessage").isNotEmpty())
				.andExpect(jsonPath("$.files[0].storedFilePath").doesNotExist());
		} finally {
			List<ImportFile> importedFiles = importFileMapper.selectList(
				new LambdaQueryWrapper<ImportFile>()
					.eq(ImportFile::getOriginalFileName, imageFileName)
			);
			for (ImportFile importFile : importedFiles) {
				if (importFile.getStoredFilePath() != null) {
					Files.deleteIfExists(Path.of(importFile.getStoredFilePath()));
				}
			}
			learningLibraryMapper.deleteById(library.getId());
		}
	}

	@Test
	void importBatchIsMarkedFailedWhenEveryFileFails() throws Exception {
		LearningLibrary library = createLibraryForRequestUser("failed-import-api-test-" + System.nanoTime());
		try {
			MockMultipartFile unsupportedFile = new MockMultipartFile(
				"files", "unsupported.pdf", "application/pdf", "test pdf".getBytes()
			);

			mockMvc.perform(multipart("/api/libraries/" + library.getId() + "/import-batches")
				.file(unsupportedFile)
				.with(authenticatedRequest()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("FAILED"))
				.andExpect(jsonPath("$.files[0].status").value("UPLOAD_FAILED"));
		} finally {
			learningLibraryMapper.deleteById(library.getId());
		}
	}

	@Test
	void wordImportApiAcceptsTheLongDocxMimeType() throws Exception {
		LearningLibrary library = createLibraryForRequestUser("word-import-api-test-" + System.nanoTime());
		String wordFileName = "questions-" + System.nanoTime() + ".docx";
		try {
			MockMultipartFile wordFile = new MockMultipartFile(
				"files",
				wordFileName,
				"application/vnd.openxmlformats-officedocument.wordprocessingml.document",
				"test word document".getBytes()
			);

			mockMvc.perform(multipart("/api/libraries/" + library.getId() + "/import-batches")
				.file(wordFile)
				.with(authenticatedRequest()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.files[0].originalFileName").value(wordFileName))
				.andExpect(jsonPath("$.files[0].id").isNumber())
				.andExpect(jsonPath("$.files[0].totalChunkCount").value(0))
				.andExpect(jsonPath("$.files[0].completedChunkCount").value(0))
				.andExpect(jsonPath("$.files[0].generatedDraftCount").value(0));
		} finally {
			List<ImportFile> importedFiles = importFileMapper.selectList(
				new LambdaQueryWrapper<ImportFile>()
					.eq(ImportFile::getOriginalFileName, wordFileName)
			);
			for (ImportFile importFile : importedFiles) {
				if (importFile.getStoredFilePath() != null) {
					Files.deleteIfExists(Path.of(importFile.getStoredFilePath()));
				}
			}
			learningLibraryMapper.deleteById(library.getId());
		}
	}

	@Test
	void importFileDraftsCanBeReadOnlyFromItsOwnLibrary() throws Exception {
		LearningLibrary ownerLibrary = createLibraryForRequestUser("draft-owner-" + System.nanoTime());
		LearningLibrary otherLibrary = createLibraryForRequestUser("draft-other-" + System.nanoTime());
		String imageFileName = "draft-source-" + System.nanoTime() + ".png";
		try {
			MvcResult uploadResult = mockMvc.perform(
				multipart("/api/libraries/" + ownerLibrary.getId() + "/import-batches")
					.file(new MockMultipartFile("files", imageFileName, "image/png", "test image".getBytes()))
					.with(authenticatedRequest())
			).andExpect(status().isCreated()).andReturn();

			String responseBody = uploadResult.getResponse().getContentAsString();
			Long importFileId = jdbcTemplate.queryForObject(
				"SELECT id FROM import_file WHERE original_file_name = ?", Long.class, imageFileName
			);

			assertNotNull(responseBody);
			mockMvc.perform(get("/api/libraries/" + ownerLibrary.getId()
				+ "/import-batches/files/" + importFileId + "/drafts")
				.with(authenticatedRequest()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isEmpty());

			mockMvc.perform(get("/api/libraries/" + otherLibrary.getId()
				+ "/import-batches/files/" + importFileId + "/drafts")
				.with(authenticatedRequest()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("导入文件不属于当前学习库"));
		} finally {
			List<ImportFile> importedFiles = importFileMapper.selectList(
				new LambdaQueryWrapper<ImportFile>()
					.eq(ImportFile::getOriginalFileName, imageFileName)
			);
			for (ImportFile importFile : importedFiles) {
				if (importFile.getStoredFilePath() != null) {
					Files.deleteIfExists(Path.of(importFile.getStoredFilePath()));
				}
			}
			learningLibraryMapper.deleteById(ownerLibrary.getId());
			learningLibraryMapper.deleteById(otherLibrary.getId());
		}
	}

	@Test
	void validRecognitionResultCreatesDraftsButNeverCreatesFormalQuestions() throws Exception {
		LearningLibrary library = createLibraryForRequestUser("recognition-draft-" + System.nanoTime());
		String imageFileName = "recognition-source-" + System.nanoTime() + ".png";
		try {
			mockMvc.perform(multipart("/api/libraries/" + library.getId() + "/import-batches")
				.file(new MockMultipartFile("files", imageFileName, "image/png", "test image".getBytes()))
				.with(authenticatedRequest()))
				.andExpect(status().isCreated());

			Long importFileId = jdbcTemplate.queryForObject(
				"SELECT id FROM import_file WHERE original_file_name = ?", Long.class, imageFileName
			);
			RecognizedQuestionOption optionA = new RecognizedQuestionOption();
			optionA.setOptionKey("A");
			optionA.setContent("管理文件");
			RecognizedQuestionOption optionB = new RecognizedQuestionOption();
			optionB.setOptionKey("B");
			optionB.setContent("分配 CPU");
			RecognizedQuestion recognizedQuestion = new RecognizedQuestion();
			recognizedQuestion.setQuestionType("SINGLE_CHOICE");
			recognizedQuestion.setStem("进程调度的作用是？");
			recognizedQuestion.setOptions(List.of(optionA, optionB));
			recognizedQuestion.setCorrectAnswer(List.of("B"));

			Long formalQuestionCountBefore = questionMapper.selectCount(
				new LambdaQueryWrapper<Question>().eq(Question::getLibraryId, library.getId())
			);
			questionDraftService.saveRecognitionResult(
				importFileId, "1. 进程调度的作用是？", List.of(recognizedQuestion)
			);

			QuestionDraft draft = questionDraftMapper.selectOne(new LambdaQueryWrapper<QuestionDraft>()
				.eq(QuestionDraft::getImportFileId, importFileId));
			assertNotNull(draft);
			assertEquals(library.getId(), draft.getLibraryId());
			assertEquals("WAITING_CONFIRMATION", draft.getStatus());
			assertEquals(formalQuestionCountBefore, questionMapper.selectCount(
				new LambdaQueryWrapper<Question>().eq(Question::getLibraryId, library.getId())
			));
			assertEquals("WAITING_CONFIRMATION", importFileMapper.selectById(importFileId).getStatus());
		} finally {
			List<ImportFile> importedFiles = importFileMapper.selectList(
				new LambdaQueryWrapper<ImportFile>().eq(ImportFile::getOriginalFileName, imageFileName)
			);
			for (ImportFile importFile : importedFiles) {
				if (importFile.getStoredFilePath() != null) {
					Files.deleteIfExists(Path.of(importFile.getStoredFilePath()));
				}
			}
			learningLibraryMapper.deleteById(library.getId());
		}
	}

	@Test
	void confirmedDraftCreatesOneFormalQuestionOnlyOnce() throws Exception {
		LearningLibrary library = createLibraryForRequestUser("confirm-draft-" + System.nanoTime());
		String imageFileName = "confirm-source-" + System.nanoTime() + ".png";
		try {
			mockMvc.perform(multipart("/api/libraries/" + library.getId() + "/import-batches")
				.file(new MockMultipartFile("files", imageFileName, "image/png", "test image".getBytes()))
				.with(authenticatedRequest()))
				.andExpect(status().isCreated());

			Long importFileId = jdbcTemplate.queryForObject(
				"SELECT id FROM import_file WHERE original_file_name = ?", Long.class, imageFileName
			);
			RecognizedQuestionOption optionA = new RecognizedQuestionOption();
			optionA.setOptionKey("A");
			optionA.setContent("1");
			RecognizedQuestionOption optionB = new RecognizedQuestionOption();
			optionB.setOptionKey("B");
			optionB.setContent("2");
			RecognizedQuestion draftQuestion = new RecognizedQuestion();
			draftQuestion.setQuestionType("SINGLE_CHOICE");
			draftQuestion.setStem("1 + 1 等于几？");
			draftQuestion.setOptions(List.of(optionA, optionB));
			draftQuestion.setCorrectAnswer(List.of("B"));
			questionDraftService.saveRecognitionResult(importFileId, "1 + 1 等于几？", List.of(draftQuestion));

			QuestionDraft draft = questionDraftMapper.selectOne(new LambdaQueryWrapper<QuestionDraft>()
				.eq(QuestionDraft::getImportFileId, importFileId));
			questionDraftService.confirmDraft(library.getId(), importFileId, draft.getId());

			assertEquals("CONFIRMED", questionDraftMapper.selectById(draft.getId()).getStatus());
			assertEquals(1, questionMapper.selectCount(
				new LambdaQueryWrapper<Question>().eq(Question::getLibraryId, library.getId())
			));
			assertThrows(IllegalStateException.class, () ->
				questionDraftService.confirmDraft(library.getId(), importFileId, draft.getId())
			);
			assertEquals(1, questionMapper.selectCount(
				new LambdaQueryWrapper<Question>().eq(Question::getLibraryId, library.getId())
			));
		} finally {
			List<ImportFile> importedFiles = importFileMapper.selectList(
				new LambdaQueryWrapper<ImportFile>().eq(ImportFile::getOriginalFileName, imageFileName)
			);
			for (ImportFile importFile : importedFiles) {
				if (importFile.getStoredFilePath() != null) {
					Files.deleteIfExists(Path.of(importFile.getStoredFilePath()));
				}
			}
			learningLibraryMapper.deleteById(library.getId());
		}
	}

	@Test
    void batchConfirmationLeavesAiAnswerIssuesForLaterReview() {
		LearningLibrary library = createLibraryForRequestUser("batch-confirm-" + System.nanoTime());
		ImportBatch batch = new ImportBatch();
		batch.setLibraryId(library.getId());
		batch.setStatus("READY_FOR_CONFIRMATION");
		importBatchMapper.insert(batch);
		ImportFile importFile = new ImportFile();
		importFile.setImportBatchId(batch.getId());
		importFile.setOriginalFileName("batch-confirm.png");
		importFile.setFileType("image/png");
		importFile.setFileSizeBytes(1L);
		importFile.setStatus("WAITING_CONFIRMATION");
		importFileMapper.insert(importFile);

		try {
			RecognizedQuestionOption optionA = new RecognizedQuestionOption();
			optionA.setOptionKey("A");
			optionA.setContent("选项 A");
			RecognizedQuestionOption optionB = new RecognizedQuestionOption();
			optionB.setOptionKey("B");
			optionB.setContent("选项 B");
			RecognizedQuestion validDraft = new RecognizedQuestion();
			validDraft.setQuestionType("SINGLE_CHOICE");
			validDraft.setStem("有答案的题目");
			validDraft.setOptions(List.of(optionA, optionB));
			validDraft.setCorrectAnswer(List.of("A"));
			RecognizedQuestion missingAnswerDraft = new RecognizedQuestion();
			missingAnswerDraft.setQuestionType("SINGLE_CHOICE");
			missingAnswerDraft.setStem("待补答案的题目");
			missingAnswerDraft.setOptions(List.of(optionA, optionB));
			missingAnswerDraft.setCorrectAnswer(List.of());
			questionDraftService.saveRecognitionResult(
				importFile.getId(), "批量确认测试", List.of(validDraft, missingAnswerDraft)
			);

			BatchDraftConfirmResponse response = importService.confirmAllDrafts(
				library.getId(), batch.getId(), requestUser.getId()
			);
			assertEquals(1, response.getConfirmedCount());
            assertEquals(0, response.getFailedDrafts().size());
			assertEquals(1, questionMapper.selectCount(
				new LambdaQueryWrapper<Question>().eq(Question::getLibraryId, library.getId())
			));
			assertEquals(1, questionDraftMapper.selectCount(new LambdaQueryWrapper<QuestionDraft>()
				.eq(QuestionDraft::getImportFileId, importFile.getId())
                .eq(QuestionDraft::getStatus, "NEEDS_REVIEW")));
		} finally {
			learningLibraryMapper.deleteById(library.getId());
		}
	}

	@Test
	void structuredFileCreatesDraftsButNeverCreatesFormalQuestions() throws Exception {
		LearningLibrary library = createLibraryForRequestUser("llm-structure-" + System.nanoTime());
		String imageFileName = "llm-source-" + System.nanoTime() + ".png";
		try {
			mockMvc.perform(multipart("/api/libraries/" + library.getId() + "/import-batches")
				.file(new MockMultipartFile("files", imageFileName, "image/png", "test image".getBytes()))
				.with(authenticatedRequest()))
				.andExpect(status().isCreated());

			Long importFileId = jdbcTemplate.queryForObject(
				"SELECT id FROM import_file WHERE original_file_name = ?", Long.class, imageFileName
			);
			ImportFile importFile = importFileMapper.selectById(importFileId);
			importFile.setStatus("WAITING_STRUCTURING");
			importFile.setRecognitionText("""
				1. 1 + 1 等于几？
				A. 1
				B. 2
				答案：B
				""");
			importFileMapper.updateById(importFile);

			Long formalQuestionCountBefore = questionMapper.selectCount(
				new LambdaQueryWrapper<Question>().eq(Question::getLibraryId, library.getId())
			);
			ImportFileResponse result = importService.structureFile(library.getId(), importFileId);

			assertEquals("WAITING_CONFIRMATION", result.getStatus());
			assertTrue(questionDraftMapper.selectCount(
				new LambdaQueryWrapper<QuestionDraft>().eq(QuestionDraft::getImportFileId, importFileId)
			) > 0);
			assertEquals(formalQuestionCountBefore, questionMapper.selectCount(
				new LambdaQueryWrapper<Question>().eq(Question::getLibraryId, library.getId())
			));
		} finally {
			List<ImportFile> importedFiles = importFileMapper.selectList(
				new LambdaQueryWrapper<ImportFile>().eq(ImportFile::getOriginalFileName, imageFileName)
			);
			for (ImportFile importFile : importedFiles) {
				if (importFile.getStoredFilePath() != null) {
					Files.deleteIfExists(Path.of(importFile.getStoredFilePath()));
				}
			}
			learningLibraryMapper.deleteById(library.getId());
		}
	}

	@Test
	void mysqlConnectionWorks() {
		Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
		assertEquals(1, result);
	}

	@Test
	void learningLibraryMapperIsAvailable() {
		assertNotNull(learningLibraryMapper);
	}

	@Test
	void learningLibraryTableCanBeRead() {
		Long count = learningLibraryMapper.selectCount(null);
		assertNotNull(count);
	}

	@Test
	void learningLibraryServiceIsAvailable() {
		assertNotNull(learningLibraryService);
	}

	@Test
	void blankLibraryNameIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> learningLibraryService.validateName("   "));
	}

	@Test
	void libraryCanBeCreatedAndUpdated() {
		LearningLibrary library = createLibraryForRequestUser("自动测试学习库");
		try {
			assertNotNull(library.getId());
			assertNotNull(library.getOwnerId());
			LearningLibrary updatedLibrary = learningLibraryService.update(library.getId(), "更新后的学习库", requestUser.getId());
			assertEquals("更新后的学习库", updatedLibrary.getName());
			LearningLibrary savedLibrary = learningLibraryService.findById(library.getId());
			assertNotNull(savedLibrary);
			assertEquals("更新后的学习库", savedLibrary.getName());
		} finally {
			learningLibraryMapper.deleteById(library.getId());
		}
	}

	@Test
	void learningLibrariesCanBeListed() {
		List<LearningLibrary> libraries = learningLibraryService.findAllOwnedBy(requestUser.getId());
		assertNotNull(libraries);
	}

	@Test
	void learningLibrariesAreScopedToTheirAuthenticatedOwner() throws Exception {
		LearningLibrary ownLibrary = createLibraryForRequestUser("own-library-" + System.nanoTime());
		AppUser otherUser = new AppUser();
		otherUser.setUsername("other-owner-" + System.nanoTime());
		otherUser.setPasswordHash(passwordEncoder.encode("demo-password-123"));
		appUserMapper.insert(otherUser);
		LearningLibrary otherLibrary = learningLibraryService.create(
			"other-library-" + System.nanoTime(), otherUser.getId()
		);
		String createdViaApiName = "api-owned-library-" + System.nanoTime();

		try {
			List<LearningLibrary> visibleLibraries = learningLibraryService.findAllOwnedBy(requestUser.getId());
			assertTrue(visibleLibraries.stream().anyMatch(library -> library.getId().equals(ownLibrary.getId())));
			assertFalse(visibleLibraries.stream().anyMatch(library -> library.getId().equals(otherLibrary.getId())));
			assertThrows(AccessDeniedException.class, () ->
				learningLibraryService.findOwnedById(otherLibrary.getId(), requestUser.getId())
			);

			mockMvc.perform(get("/api/libraries/" + otherLibrary.getId()).with(authenticatedRequest()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("无权访问该学习库"));

			mockMvc.perform(patch("/api/libraries/" + otherLibrary.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"attempted rename\"}")
				.with(authenticatedRequest()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("无权访问该学习库"));

			mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
				"/api/libraries/" + otherLibrary.getId()
			).with(authenticatedRequest()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("无权访问该学习库"));

			mockMvc.perform(post("/api/libraries")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"" + createdViaApiName + "\"}")
				.with(authenticatedRequest()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ownerId").value(requestUser.getId()));
		} finally {
			LearningLibrary createdViaApi = learningLibraryMapper.selectOne(
				new LambdaQueryWrapper<LearningLibrary>().eq(LearningLibrary::getName, createdViaApiName)
			);
			if (createdViaApi != null) {
				learningLibraryMapper.deleteById(createdViaApi.getId());
			}
			learningLibraryMapper.deleteById(ownLibrary.getId());
			learningLibraryMapper.deleteById(otherLibrary.getId());
			appUserMapper.deleteById(otherUser.getId());
		}
	}

	@Test
	void libraryCanBeDeleted() {
		LearningLibrary library = createLibraryForRequestUser("temporary-delete-test");
		learningLibraryService.delete(library.getId(), requestUser.getId());
		assertNull(learningLibraryService.findById(library.getId()));
	}

	@Test
	void questionCanBeCreatedAndFoundByLibrary() {
		LearningLibrary library = createLibraryForRequestUser("question-api-test");
		try {
			CreateQuestionOptionRequest optionA = new CreateQuestionOptionRequest();
			optionA.setOptionKey("A");
			optionA.setContent("wrong answer");
			optionA.setSortOrder(1);

			CreateQuestionOptionRequest optionB = new CreateQuestionOptionRequest();
			optionB.setOptionKey("B");
			optionB.setContent("correct answer");
			optionB.setSortOrder(2);

			CreateQuestionRequest request = new CreateQuestionRequest();
			request.setLibraryId(library.getId());
			request.setQuestionType("SINGLE_CHOICE");
			request.setStem("Which option is correct?");
			request.setCorrectAnswer(List.of("B"));
			request.setOptions(List.of(optionA, optionB));

			Question question = questionService.create(request, requestUser.getId());
			assertNotNull(question.getId());

			List<Question> questions = questionService.findByLibraryId(library.getId(), requestUser.getId());
			assertEquals(1, questions.size());
			assertEquals(question.getId(), questions.get(0).getId());
		} finally {
			learningLibraryMapper.deleteById(library.getId());
		}
	}

	@Test
	void importEndpointsAreScopedToTheirAuthenticatedOwner() throws Exception {
		LearningLibrary ownLibrary = createLibraryForRequestUser("import-own-library-" + System.nanoTime());
		AppUser otherUser = new AppUser();
		otherUser.setUsername("import-other-owner-" + System.nanoTime());
		otherUser.setPasswordHash(passwordEncoder.encode("demo-password-123"));
		appUserMapper.insert(otherUser);
		LearningLibrary otherLibrary = learningLibraryService.create(
			"import-other-library-" + System.nanoTime(), otherUser.getId()
		);
		ImportBatch otherBatch = new ImportBatch();
		otherBatch.setLibraryId(otherLibrary.getId());
		otherBatch.setStatus("WAITING_CONFIRMATION");
		importBatchMapper.insert(otherBatch);
		ImportFile otherFile = new ImportFile();
		otherFile.setImportBatchId(otherBatch.getId());
		otherFile.setOriginalFileName("private-question.png");
		otherFile.setStoredFilePath("C:\\private\\private-question.png");
		otherFile.setFileType("image/png");
		otherFile.setFileSizeBytes(1L);
		otherFile.setStatus("WAITING_CONFIRMATION");
		otherFile.setRecognitionText("private question text");
		importFileMapper.insert(otherFile);
		QuestionDraft otherDraft = new QuestionDraft();
		otherDraft.setLibraryId(otherLibrary.getId());
		otherDraft.setImportFileId(otherFile.getId());
		otherDraft.setSortOrder(1);
		otherDraft.setStatus("WAITING_CONFIRMATION");
		otherDraft.setQuestionType("SINGLE_CHOICE");
		otherDraft.setStem("private draft");
		otherDraft.setCorrectAnswer("[\"A\"]");
		questionDraftMapper.insert(otherDraft);

		try {
			MockMultipartFile upload = new MockMultipartFile(
				"files", "attempted-upload.png", "image/png", "image bytes".getBytes()
			);
			String basePath = "/api/libraries/" + otherLibrary.getId() + "/import-batches";

			mockMvc.perform(multipart(basePath).file(upload).with(authenticatedRequest()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("无权访问该学习库"));

			mockMvc.perform(get(basePath + "/latest").with(authenticatedRequest()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("无权访问该学习库"));

			mockMvc.perform(get(basePath + "/" + otherBatch.getId() + "/drafts")
				.with(authenticatedRequest()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("无权访问该学习库"));

			String draftPath = basePath + "/files/" + otherFile.getId() + "/drafts/" + otherDraft.getId();
			mockMvc.perform(get(basePath + "/files/" + otherFile.getId() + "/drafts")
				.with(authenticatedRequest()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("无权访问该学习库"));

			mockMvc.perform(patch(draftPath).contentType(MediaType.APPLICATION_JSON)
				.content("{}").with(authenticatedRequest()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("无权访问该学习库"));

			mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(draftPath)
				.with(authenticatedRequest()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("无权访问该学习库"));

			mockMvc.perform(post(draftPath + "/confirm").with(authenticatedRequest()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("无权访问该学习库"));

			String filePath = basePath + "/files/" + otherFile.getId();
			mockMvc.perform(post(filePath + "/recognize").with(authenticatedRequest()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("无权访问该学习库"));

			mockMvc.perform(post(filePath + "/structure").with(authenticatedRequest()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("无权访问该学习库"));

			assertEquals("WAITING_CONFIRMATION", importFileMapper.selectById(otherFile.getId()).getStatus());
			assertNotNull(questionDraftMapper.selectById(otherDraft.getId()));
			assertEquals(0, questionMapper.selectCount(new LambdaQueryWrapper<Question>()
				.eq(Question::getLibraryId, otherLibrary.getId())));
		} finally {
			learningLibraryMapper.deleteById(ownLibrary.getId());
			learningLibraryMapper.deleteById(otherLibrary.getId());
			appUserMapper.deleteById(otherUser.getId());
		}
	}

	@Test
	void questionEndpointsAreScopedToTheirAuthenticatedOwner() throws Exception {
		LearningLibrary ownLibrary = createLibraryForRequestUser("question-own-library-" + System.nanoTime());
		AppUser otherUser = new AppUser();
		otherUser.setUsername("question-other-owner-" + System.nanoTime());
		otherUser.setPasswordHash(passwordEncoder.encode("demo-password-123"));
		appUserMapper.insert(otherUser);
		LearningLibrary otherLibrary = learningLibraryService.create(
			"question-other-library-" + System.nanoTime(), otherUser.getId()
		);
		Question otherQuestion = questionService.create(
			createSingleChoiceQuestionRequest(otherLibrary.getId(), "private question"), otherUser.getId()
		);

		try {
			mockMvc.perform(get("/api/questions/library/" + otherLibrary.getId())
				.with(authenticatedRequest()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("无权访问该学习库"));

			mockMvc.perform(get("/api/questions/" + otherQuestion.getId())
				.with(authenticatedRequest()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("无权访问该学习库"));

			mockMvc.perform(post("/api/questions")
				.contentType(MediaType.APPLICATION_JSON)
				.content(questionRequestJson(otherLibrary.getId(), "attempted create"))
				.with(authenticatedRequest()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("无权访问该学习库"));

			mockMvc.perform(patch("/api/questions/" + otherQuestion.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(questionUpdateJson("attempted update"))
				.with(authenticatedRequest()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("无权访问该学习库"));

			mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
				"/api/questions/" + otherQuestion.getId()
			).with(authenticatedRequest()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("无权访问该学习库"));

			assertNotNull(questionMapper.selectById(otherQuestion.getId()));
			assertTrue(questionService.findByLibraryId(ownLibrary.getId(), requestUser.getId()).isEmpty());
		} finally {
			learningLibraryMapper.deleteById(ownLibrary.getId());
			learningLibraryMapper.deleteById(otherLibrary.getId());
			appUserMapper.deleteById(otherUser.getId());
		}
	}

	@Test
	void answerRecordCanBeInsertedAndFound() {
		LearningLibrary library = createLibraryForRequestUser("answer-record-mapper-test");
		try {
			Question question = new Question();
			question.setLibraryId(library.getId());
			question.setQuestionType("SINGLE_CHOICE");
			question.setStem("Mapper insert test question");
			question.setCorrectAnswer("[\"A\"]");
			questionMapper.insert(question);

			AnswerRecord record = new AnswerRecord();
			record.setLibraryId(library.getId());
			record.setQuestionId(question.getId());
			record.setSelectedAnswer("[\"B\"]");
			record.setCorrect(false);

			int affectedRows = answerRecordMapper.insert(record);
			assertEquals(1, affectedRows);
			assertNotNull(record.getId());

			AnswerRecord savedRecord = answerRecordMapper.selectById(record.getId());
			assertNotNull(savedRecord);
			assertEquals(false, savedRecord.getCorrect());
		} finally {
			learningLibraryMapper.deleteById(library.getId());
		}
	}

	@Test
	void wrongQuestionCanBeUpdatedAndDeleted() {
		LearningLibrary library = createLibraryForRequestUser("wrong-question-mapper-test");
		try {
			Question question = new Question();
			question.setLibraryId(library.getId());
			question.setQuestionType("SINGLE_CHOICE");
			question.setStem("Mapper update and delete test question");
			question.setCorrectAnswer("[\"A\"]");
			questionMapper.insert(question);

			WrongQuestion wrongQuestion = new WrongQuestion();
			wrongQuestion.setLibraryId(library.getId());
			wrongQuestion.setQuestionId(question.getId());
			wrongQuestion.setConsecutiveCorrectCount(0);
			wrongQuestionMapper.insert(wrongQuestion);

			wrongQuestion.setConsecutiveCorrectCount(1);
			assertEquals(1, wrongQuestionMapper.updateById(wrongQuestion));

			WrongQuestion savedWrongQuestion = wrongQuestionMapper.selectById(wrongQuestion.getId());
			assertNotNull(savedWrongQuestion);
			assertEquals(1, savedWrongQuestion.getConsecutiveCorrectCount());

			assertEquals(1, wrongQuestionMapper.deleteById(wrongQuestion.getId()));
			assertNull(wrongQuestionMapper.selectById(wrongQuestion.getId()));
		} finally {
			learningLibraryMapper.deleteById(library.getId());
		}
	}

	@Test
	void practiceEndpointsAreScopedToTheirAuthenticatedOwner() throws Exception {
		LearningLibrary ownLibrary = createLibraryForRequestUser("practice-own-library-" + System.nanoTime());
		AppUser otherUser = new AppUser();
		otherUser.setUsername("practice-other-owner-" + System.nanoTime());
		otherUser.setPasswordHash(passwordEncoder.encode("demo-password-123"));
		appUserMapper.insert(otherUser);
		LearningLibrary otherLibrary = learningLibraryService.create(
			"practice-other-library-" + System.nanoTime(), otherUser.getId()
		);
		Question otherQuestion = new Question();
		otherQuestion.setLibraryId(otherLibrary.getId());
		otherQuestion.setQuestionType("SINGLE_CHOICE");
		otherQuestion.setStem("private practice question");
		otherQuestion.setCorrectAnswer("[\"A\"]");
		questionMapper.insert(otherQuestion);

		try {
			mockMvc.perform(post("/api/practice/answers")
				.contentType(MediaType.APPLICATION_JSON)
				.content(submitAnswerJson(otherLibrary.getId(), otherQuestion.getId(), "A"))
				.with(authenticatedRequest()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("无权访问该学习库"));

			mockMvc.perform(get("/api/practice/wrong-questions/library/" + otherLibrary.getId())
				.with(authenticatedRequest()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("无权访问该学习库"));

			mockMvc.perform(post("/api/practice/answers")
				.contentType(MediaType.APPLICATION_JSON)
				.content(submitAnswerJson(ownLibrary.getId(), otherQuestion.getId(), "A"))
				.with(authenticatedRequest()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("题目不存在或不属于该学习库"));

			assertEquals(0, answerRecordMapper.selectCount(new LambdaQueryWrapper<AnswerRecord>()
				.eq(AnswerRecord::getQuestionId, otherQuestion.getId())));
			assertEquals(0, wrongQuestionMapper.selectCount(new LambdaQueryWrapper<WrongQuestion>()
				.eq(WrongQuestion::getQuestionId, otherQuestion.getId())));
		} finally {
			learningLibraryMapper.deleteById(ownLibrary.getId());
			learningLibraryMapper.deleteById(otherLibrary.getId());
			appUserMapper.deleteById(otherUser.getId());
		}
	}

	@Test
	void wrongQuestionRuleWorksAcrossAnswerAttempts() {
		LearningLibrary library = createLibraryForRequestUser("practice-rule-test");
		try {
			Question question = new Question();
			question.setLibraryId(library.getId());
			question.setQuestionType("SINGLE_CHOICE");
			question.setStem("Practice rule test question");
			question.setCorrectAnswer("[\"A\"]");
			questionMapper.insert(question);

			SubmitAnswerResponse firstWrong = practiceService.submitAnswer(
				answer(library.getId(), question.getId(), "B"), requestUser.getId()
			);
			assertEquals(false, firstWrong.getCorrect());
			assertEquals(0, firstWrong.getConsecutiveCorrectCount());
			assertEquals(1, practiceService.findWrongQuestionsByLibraryId(
				library.getId(), requestUser.getId()
			).size());

			SubmitAnswerResponse firstCorrect = practiceService.submitAnswer(
				answer(library.getId(), question.getId(), "A"), requestUser.getId()
			);
			assertEquals(1, firstCorrect.getConsecutiveCorrectCount());

			SubmitAnswerResponse resetByWrongAnswer = practiceService.submitAnswer(
				answer(library.getId(), question.getId(), "B"), requestUser.getId()
			);
			assertEquals(0, resetByWrongAnswer.getConsecutiveCorrectCount());

			assertEquals(1, practiceService.submitAnswer(
				answer(library.getId(), question.getId(), "A"), requestUser.getId()
			).getConsecutiveCorrectCount());
			SubmitAnswerResponse secondCorrect = practiceService.submitAnswer(
				answer(library.getId(), question.getId(), "A"), requestUser.getId()
			);
			assertEquals(true, secondCorrect.getRemovedFromWrongQuestions());
			assertEquals(0, practiceService.findWrongQuestionsByLibraryId(
				library.getId(), requestUser.getId()
			).size());
			assertEquals(0, wrongQuestionMapper.selectCount(new LambdaQueryWrapper<WrongQuestion>()
				.eq(WrongQuestion::getLibraryId, library.getId())
				.eq(WrongQuestion::getQuestionId, question.getId())));
			assertEquals(5, answerRecordMapper.selectCount(new LambdaQueryWrapper<AnswerRecord>()
				.eq(AnswerRecord::getLibraryId, library.getId())));
		} finally {
			learningLibraryMapper.deleteById(library.getId());
		}
	}

	private SubmitAnswerRequest answer(Long libraryId, Long questionId, String selectedAnswer) {
		SubmitAnswerRequest request = new SubmitAnswerRequest();
		request.setLibraryId(libraryId);
		request.setQuestionId(questionId);
		request.setSelectedAnswer(List.of(selectedAnswer));
		return request;
	}

	private String submitAnswerJson(Long libraryId, Long questionId, String selectedAnswer) {
		return """
			{
			  "libraryId": %d,
			  "questionId": %d,
			  "selectedAnswer": ["%s"]
			}
			""".formatted(libraryId, questionId, selectedAnswer);
	}

	private RequestPostProcessor authenticatedRequest() {
		return request -> {
			request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + requestUserAccessToken);
			return request;
		};
	}

	private LearningLibrary createLibraryForRequestUser(String name) {
		return learningLibraryService.create(name, requestUser.getId());
	}

	private CreateQuestionRequest createSingleChoiceQuestionRequest(Long libraryId, String stem) {
		CreateQuestionOptionRequest optionA = new CreateQuestionOptionRequest();
		optionA.setOptionKey("A");
		optionA.setContent("wrong answer");
		optionA.setSortOrder(1);

		CreateQuestionOptionRequest optionB = new CreateQuestionOptionRequest();
		optionB.setOptionKey("B");
		optionB.setContent("correct answer");
		optionB.setSortOrder(2);

		CreateQuestionRequest request = new CreateQuestionRequest();
		request.setLibraryId(libraryId);
		request.setQuestionType("SINGLE_CHOICE");
		request.setStem(stem);
		request.setCorrectAnswer(List.of("B"));
		request.setOptions(List.of(optionA, optionB));
		return request;
	}

	private String questionRequestJson(Long libraryId, String stem) {
		return """
			{
			  "libraryId": %d,
			  "questionType": "SINGLE_CHOICE",
			  "stem": "%s",
			  "correctAnswer": ["B"],
			  "options": [
			    {"optionKey": "A", "content": "wrong answer", "sortOrder": 1},
			    {"optionKey": "B", "content": "correct answer", "sortOrder": 2}
			  ]
			}
			""".formatted(libraryId, stem);
	}

	private String questionUpdateJson(String stem) {
		return """
			{
			  "questionType": "SINGLE_CHOICE",
			  "stem": "%s",
			  "correctAnswer": ["B"],
			  "options": [
			    {"optionKey": "A", "content": "wrong answer", "sortOrder": 1},
			    {"optionKey": "B", "content": "correct answer", "sortOrder": 2}
			  ]
			}
			""".formatted(stem);
	}

	private String findAvailableTwoCharacterUsername() {
		for (char first = 'a'; first <= 'z'; first++) {
			for (char second = 'a'; second <= 'z'; second++) {
				String candidate = "" + first + second;
				if (authService.findByUsername(candidate) == null) {
					return candidate;
				}
			}
		}

		throw new IllegalStateException("没有可用的两字符测试用户名");
	}

}
