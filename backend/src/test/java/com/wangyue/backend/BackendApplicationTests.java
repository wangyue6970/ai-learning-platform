package com.wangyue.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wangyue.backend.dto.CreateQuestionOptionRequest;
import com.wangyue.backend.dto.CreateQuestionRequest;
import com.wangyue.backend.dto.SubmitAnswerRequest;
import com.wangyue.backend.dto.SubmitAnswerResponse;
import com.wangyue.backend.entity.AnswerRecord;
import com.wangyue.backend.entity.LearningLibrary;
import com.wangyue.backend.entity.Question;
import com.wangyue.backend.entity.WrongQuestion;
import com.wangyue.backend.mapper.AnswerRecordMapper;
import com.wangyue.backend.mapper.LearningLibraryMapper;
import com.wangyue.backend.mapper.QuestionMapper;
import com.wangyue.backend.mapper.WrongQuestionMapper;
import com.wangyue.backend.service.LearningLibraryService;
import com.wangyue.backend.service.QuestionService;
import com.wangyue.backend.service.PracticeService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
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

	@Test
	void passwordIsHashedAndCanBeVerified() {
		String rawPassword = "demo-password-123";
		String passwordHash = passwordEncoder.encode(rawPassword);

		assertNotEquals(rawPassword, passwordHash);
		assertEquals(true, passwordEncoder.matches(rawPassword, passwordHash));
		assertEquals(false, passwordEncoder.matches("wrong-password", passwordHash));
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
