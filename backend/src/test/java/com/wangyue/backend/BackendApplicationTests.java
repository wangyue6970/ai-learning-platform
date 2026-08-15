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
import com.wangyue.backend.dto.RegisterRequest;
import com.wangyue.backend.dto.ImportFileResponse;
import com.wangyue.backend.dto.RecognizedQuestion;
import com.wangyue.backend.dto.RecognizedQuestionOption;
import com.wangyue.backend.dto.SubmitAnswerRequest;
import com.wangyue.backend.dto.SubmitAnswerResponse;
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
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.import.processing.enabled=false")
@AutoConfigureMockMvc
class BackendApplicationTests {

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
	void wordRecognitionChangesOnlyItsOwnFileStatus() throws Exception {
		LearningLibrary library = learningLibraryService.create("word-flow-test-" + System.nanoTime());
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
		LearningLibrary library = learningLibraryService.create("ocr-flow-test-" + System.nanoTime());
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
		LearningLibrary ownerLibrary = learningLibraryService.create("recognize-owner-" + System.nanoTime());
		LearningLibrary otherLibrary = learningLibraryService.create("recognize-other-" + System.nanoTime());
		String imageFileName = "recognize-source-" + System.nanoTime() + ".png";
		try {
			mockMvc.perform(multipart("/api/libraries/" + ownerLibrary.getId() + "/import-batches")
				.file(new MockMultipartFile("files", imageFileName, "image/png", "test image".getBytes())))
				.andExpect(status().isCreated());

			Long importFileId = jdbcTemplate.queryForObject(
				"SELECT id FROM import_file WHERE original_file_name = ?", Long.class, imageFileName
			);

			mockMvc.perform(post("/api/libraries/" + otherLibrary.getId()
				+ "/import-batches/files/" + importFileId + "/recognize"))
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
	void importApiKeepsOtherFilesWhenOneFileFails() throws Exception {
		LearningLibrary library = learningLibraryService.create("import-api-test-" + System.nanoTime());
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
				.file(unsupportedFile))
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
		LearningLibrary library = learningLibraryService.create("failed-import-api-test-" + System.nanoTime());
		try {
			MockMultipartFile unsupportedFile = new MockMultipartFile(
				"files", "unsupported.pdf", "application/pdf", "test pdf".getBytes()
			);

			mockMvc.perform(multipart("/api/libraries/" + library.getId() + "/import-batches")
				.file(unsupportedFile))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("FAILED"))
				.andExpect(jsonPath("$.files[0].status").value("UPLOAD_FAILED"));
		} finally {
			learningLibraryMapper.deleteById(library.getId());
		}
	}

	@Test
	void wordImportApiAcceptsTheLongDocxMimeType() throws Exception {
		LearningLibrary library = learningLibraryService.create("word-import-api-test-" + System.nanoTime());
		String wordFileName = "questions-" + System.nanoTime() + ".docx";
		try {
			MockMultipartFile wordFile = new MockMultipartFile(
				"files",
				wordFileName,
				"application/vnd.openxmlformats-officedocument.wordprocessingml.document",
				"test word document".getBytes()
			);

			mockMvc.perform(multipart("/api/libraries/" + library.getId() + "/import-batches")
				.file(wordFile))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.files[0].originalFileName").value(wordFileName))
				.andExpect(jsonPath("$.files[0].id").isNumber());
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
		LearningLibrary ownerLibrary = learningLibraryService.create("draft-owner-" + System.nanoTime());
		LearningLibrary otherLibrary = learningLibraryService.create("draft-other-" + System.nanoTime());
		String imageFileName = "draft-source-" + System.nanoTime() + ".png";
		try {
			MvcResult uploadResult = mockMvc.perform(
				multipart("/api/libraries/" + ownerLibrary.getId() + "/import-batches")
					.file(new MockMultipartFile("files", imageFileName, "image/png", "test image".getBytes()))
			).andExpect(status().isCreated()).andReturn();

			String responseBody = uploadResult.getResponse().getContentAsString();
			Long importFileId = jdbcTemplate.queryForObject(
				"SELECT id FROM import_file WHERE original_file_name = ?", Long.class, imageFileName
			);

			assertNotNull(responseBody);
			mockMvc.perform(get("/api/libraries/" + ownerLibrary.getId()
				+ "/import-batches/files/" + importFileId + "/drafts"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isEmpty());

			mockMvc.perform(get("/api/libraries/" + otherLibrary.getId()
				+ "/import-batches/files/" + importFileId + "/drafts"))
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
		LearningLibrary library = learningLibraryService.create("recognition-draft-" + System.nanoTime());
		String imageFileName = "recognition-source-" + System.nanoTime() + ".png";
		try {
			mockMvc.perform(multipart("/api/libraries/" + library.getId() + "/import-batches")
				.file(new MockMultipartFile("files", imageFileName, "image/png", "test image".getBytes())))
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
		LearningLibrary library = learningLibraryService.create("confirm-draft-" + System.nanoTime());
		String imageFileName = "confirm-source-" + System.nanoTime() + ".png";
		try {
			mockMvc.perform(multipart("/api/libraries/" + library.getId() + "/import-batches")
				.file(new MockMultipartFile("files", imageFileName, "image/png", "test image".getBytes())))
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
	void structuredFileCreatesDraftsButNeverCreatesFormalQuestions() throws Exception {
		LearningLibrary library = learningLibraryService.create("llm-structure-" + System.nanoTime());
		String imageFileName = "llm-source-" + System.nanoTime() + ".png";
		try {
			mockMvc.perform(multipart("/api/libraries/" + library.getId() + "/import-batches")
				.file(new MockMultipartFile("files", imageFileName, "image/png", "test image".getBytes())))
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
		LearningLibrary library = learningLibraryService.create("自动测试学习库");
		try {
			assertNotNull(library.getId());
			assertNotNull(library.getOwnerId());
			LearningLibrary updatedLibrary = learningLibraryService.update(library.getId(), "更新后的学习库");
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
		List<LearningLibrary> libraries = learningLibraryService.findAll();
		assertNotNull(libraries);
	}

	@Test
	void libraryCanBeDeleted() {
		LearningLibrary library = learningLibraryService.create("temporary-delete-test");
		learningLibraryService.delete(library.getId());
		assertNull(learningLibraryService.findById(library.getId()));
	}

	@Test
	void questionCanBeCreatedAndFoundByLibrary() {
		LearningLibrary library = learningLibraryService.create("question-api-test");
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

			Question question = questionService.create(request);
			assertNotNull(question.getId());

			List<Question> questions = questionService.findByLibraryId(library.getId());
			assertEquals(1, questions.size());
			assertEquals(question.getId(), questions.get(0).getId());
		} finally {
			learningLibraryMapper.deleteById(library.getId());
		}
	}

	@Test
	void answerRecordCanBeInsertedAndFound() {
		LearningLibrary library = learningLibraryService.create("answer-record-mapper-test");
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
		LearningLibrary library = learningLibraryService.create("wrong-question-mapper-test");
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
	void wrongQuestionRuleWorksAcrossAnswerAttempts() {
		LearningLibrary library = learningLibraryService.create("practice-rule-test");
		try {
			Question question = new Question();
			question.setLibraryId(library.getId());
			question.setQuestionType("SINGLE_CHOICE");
			question.setStem("Practice rule test question");
			question.setCorrectAnswer("[\"A\"]");
			questionMapper.insert(question);

			SubmitAnswerResponse firstWrong = practiceService.submitAnswer(answer(library.getId(), question.getId(), "B"));
			assertEquals(false, firstWrong.getCorrect());
			assertEquals(0, firstWrong.getConsecutiveCorrectCount());
			assertEquals(1, practiceService.findWrongQuestionsByLibraryId(library.getId()).size());

			SubmitAnswerResponse firstCorrect = practiceService.submitAnswer(answer(library.getId(), question.getId(), "A"));
			assertEquals(1, firstCorrect.getConsecutiveCorrectCount());

			SubmitAnswerResponse resetByWrongAnswer = practiceService.submitAnswer(answer(library.getId(), question.getId(), "B"));
			assertEquals(0, resetByWrongAnswer.getConsecutiveCorrectCount());

			assertEquals(1, practiceService.submitAnswer(answer(library.getId(), question.getId(), "A")).getConsecutiveCorrectCount());
			SubmitAnswerResponse secondCorrect = practiceService.submitAnswer(answer(library.getId(), question.getId(), "A"));
			assertEquals(true, secondCorrect.getRemovedFromWrongQuestions());
			assertEquals(0, practiceService.findWrongQuestionsByLibraryId(library.getId()).size());
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

}
