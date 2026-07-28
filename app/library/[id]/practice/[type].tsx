import { router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { Pressable, Text, View } from 'react-native';
import { initialQuestions } from '../../../../data/questions';

export default function PracticeScreen() {
  const { id, type } = useLocalSearchParams<{ id: string; type: string }>();
  const [selectedOptionIds, setSelectedOptionIds] = useState<string[]>([]);
  const [currentQuestionIndex, setCurrentQuestionIndex] = useState(0);
  const practiceQuestions = initialQuestions.filter(
    (question) => question.libraryId === id && question.type === type
  );

  if (practiceQuestions.length === 0) {
    return <Text>当前题型暂无题目。</Text>;
  }

  const currentQuestion = practiceQuestions[currentQuestionIndex];
  const isLastQuestion = currentQuestionIndex === practiceQuestions.length - 1;

  function selectOption(optionId: string) {
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
    if (isLastQuestion) {
      router.back();
      return;
    }

    setSelectedOptionIds([]);
    setCurrentQuestionIndex((currentIndex) => currentIndex + 1);
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
      <Pressable onPress={goToNextQuestion} style={{ marginTop: 28, padding: 14 }}>
        <Text>{isLastQuestion ? '完成本题型' : '下一题'}</Text>
      </Pressable>
    </View>
  );
}
