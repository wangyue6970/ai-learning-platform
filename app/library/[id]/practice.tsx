import { router, useLocalSearchParams } from 'expo-router';
import { Pressable, Text, View } from 'react-native';
import { useQuestionLearning } from '../../../contexts/QuestionLearningContext';
import { initialQuestions } from '../../../data/questions';

export default function PracticeOverviewScreen() {
  const { id, mode } = useLocalSearchParams<{ id: string; mode?: string }>();
  const { learningStatuses } = useQuestionLearning();
  const isWrongPractice = mode === 'wrong';
  const libraryQuestions = initialQuestions.filter(
    (question) =>
      question.libraryId === id &&
      (!isWrongPractice ||
        learningStatuses.some(
          (status) => status.questionId === question.id && status.isInWrongSet
        ))
  );
  const singleChoiceCount = libraryQuestions.filter((question) => question.type === 'single_choice').length;
  const multipleChoiceCount = libraryQuestions.filter((question) => question.type === 'multiple_choice').length;
  const trueFalseCount = libraryQuestions.filter((question) => question.type === 'true_false').length;

  return (
    <View style={{ flex: 1, padding: 20, paddingTop: 64 }}>
      <Text style={{ fontSize: 26, fontWeight: '700' }}>
        {isWrongPractice ? '错题集练习' : '完整题库练习'}
      </Text>
      <Text style={{ marginTop: 12 }}>本次会按题型集中练习。</Text>
      <Pressable
        onPress={() =>
          router.push({
            pathname: '/library/[id]/practice/[type]',
            params: { id, type: 'single_choice', mode },
          })
        }>
        <Text style={{ marginTop: 28 }}>单选题（{singleChoiceCount}）</Text>
      </Pressable>
      <Pressable
        onPress={() =>
          router.push({
            pathname: '/library/[id]/practice/[type]',
            params: { id, type: 'multiple_choice', mode },
          })
        }>
        <Text style={{ marginTop: 16 }}>多选题（{multipleChoiceCount}）</Text>
      </Pressable>
      <Pressable
        onPress={() =>
          router.push({
            pathname: '/library/[id]/practice/[type]',
            params: { id, type: 'true_false', mode },
          })
        }>
        <Text style={{ marginTop: 16 }}>判断题（{trueFalseCount}）</Text>
      </Pressable>
    </View>
  );
}
