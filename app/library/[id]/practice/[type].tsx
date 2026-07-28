import { router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { Alert, Pressable, Text, View } from 'react-native';
import { useQuestionLearning } from '../../../../contexts/QuestionLearningContext';
import { initialQuestions } from '../../../../data/questions';

export default function PracticeScreen() {
  const { id, type, mode } = useLocalSearchParams<{
    id: string;
    type: string;
    mode?: string;
  }>();
  const [selectedOptionIds, setSelectedOptionIds] = useState<string[]>([]);
  const [currentQuestionIndex, setCurrentQuestionIndex] = useState(0);
  const [hasSubmitted, setHasSubmitted] = useState(false);
  const [answerFeedback, setAnswerFeedback] = useState('');
  const { learningStatuses, setLearningStatuses } = useQuestionLearning();
  const isWrongPractice = mode === 'wrong';
  const practiceQuestions = initialQuestions.filter(
    (question) =>
      question.libraryId === id &&
      question.type === type &&
      (!isWrongPractice ||
        learningStatuses.some(
          (status) => status.questionId === question.id && status.isInWrongSet
        ))
  );

  if (practiceQuestions.length === 0) {
    return <Text>当前题型暂无题目。</Text>;
  }

  const currentQuestion = practiceQuestions[currentQuestionIndex];
  const isLastQuestion = currentQuestionIndex === practiceQuestions.length - 1;
  const isAnswerCorrect =
    selectedOptionIds.length === currentQuestion.correctOptionIds.length &&
    selectedOptionIds.every((optionId) => currentQuestion.correctOptionIds.includes(optionId));

  function selectOption(optionId: string) {
    if (hasSubmitted) {
      return;
    }
    if (type === 'multiple_choice') {
      setSelectedOptionIds((currentIds) =>
        currentIds.includes(optionId)
          ? currentIds.filter((selectedId) => selectedId !== optionId)
          : [...currentIds, optionId]
      );
      return;
    }

    setSelectedOptionIds([optionId]);
  }

  function goToNextQuestion() {
    if (!hasSubmitted) {
      Alert.alert('请先提交答案');
      return;
    }

    if (isLastQuestion) {
      router.back();
      return;
    }

    setSelectedOptionIds([]);
    setHasSubmitted(false);
    setAnswerFeedback('');
    setCurrentQuestionIndex((currentIndex) => currentIndex + 1);
  }

  function recordWrongAnswer() {
    setLearningStatuses((currentStatuses) => {
      const existingStatus = currentStatuses.find(
        (status) => status.questionId === currentQuestion.id
      );

      if (!existingStatus) {
        return [
          ...currentStatuses,
          {
            questionId: currentQuestion.id,
            libraryId: id,
            isInWrongSet: true,
            consecutiveCorrectCount: 0,
          },
        ];
      }

      return currentStatuses.map((status) =>
        status.questionId === currentQuestion.id
          ? { ...status, isInWrongSet: true, consecutiveCorrectCount: 0 }
          : status
      );
    });
  }

  function recordCorrectAnswer() {
    setLearningStatuses((currentStatuses) => {
      const existingStatus = currentStatuses.find(
        (status) => status.questionId === currentQuestion.id
      );

      if (!existingStatus || !existingStatus.isInWrongSet) {
        return currentStatuses;
      }

      const nextCorrectCount = existingStatus.consecutiveCorrectCount + 1;

      return currentStatuses.map((status) =>
        status.questionId === currentQuestion.id
          ? {
              ...status,
              consecutiveCorrectCount: nextCorrectCount,
              isInWrongSet: nextCorrectCount < 2,
            }
          : status
      );
    });
  }

  function submitAnswer() {
    if (selectedOptionIds.length === 0) {
      Alert.alert('请先选择答案');
      return;
    }

    const existingStatus = learningStatuses.find(
      (status) => status.questionId === currentQuestion.id
    );

    if (isAnswerCorrect) {
      if (existingStatus && existingStatus.isInWrongSet) {
        const nextCorrectCount = existingStatus.consecutiveCorrectCount + 1;
        setAnswerFeedback(
          nextCorrectCount >= 2 ? '回答正确，已移出错题集' : '回答正确，还需答对 1 次'
        );
      } else {
        setAnswerFeedback('回答正确');
      }
      recordCorrectAnswer();
    } else {
      setAnswerFeedback('回答错误，已加入错题集');
      recordWrongAnswer();
    }
    setHasSubmitted(true);
  }

  return (
    <View style={{ flex: 1, padding: 20, paddingTop: 64 }}>
      <Text>第 {currentQuestionIndex + 1} / {practiceQuestions.length} 题</Text>
      <Text style={{ fontSize: 20, marginTop: 20 }}>{currentQuestion.stem}</Text>
      {currentQuestion.options.map((option) => (
        <Pressable
          key={option.id}
          onPress={() => selectOption(option.id)}
          style={{ backgroundColor: selectedOptionIds.includes(option.id) ? '#DBEAFE' : '#F8FAFC', marginTop: 12, padding: 14 }}>
          <Text>{option.text}</Text>
        </Pressable>
      ))}
      <Pressable onPress={submitAnswer} style={{ marginTop: 28, padding: 14 }}>
        <Text>提交答案</Text>
      </Pressable>
      {hasSubmitted && <Text style={{ marginTop: 6 }}>{answerFeedback}</Text>}
      {hasSubmitted && (
        <Text style={{ marginTop: 12 }}>
          {isAnswerCorrect ? '回答正确' : '回答错误'}
        </Text>
      )}
      <Pressable onPress={goToNextQuestion} style={{ marginTop: 28, padding: 14 }}>
        <Text>{isLastQuestion ? '完成本题型' : '下一题'}</Text>
      </Pressable>
    </View>
  );
}
