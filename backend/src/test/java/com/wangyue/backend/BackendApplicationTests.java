package com.wangyue.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.wangyue.backend.dto.CreateQuestionOptionRequest;
import com.wangyue.backend.dto.CreateQuestionRequest;
import com.wangyue.backend.entity.LearningLibrary;
import com.wangyue.backend.entity.Question;
import com.wangyue.backend.mapper.LearningLibraryMapper;
import com.wangyue.backend.service.LearningLibraryService;
import com.wangyue.backend.service.QuestionService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

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

}
