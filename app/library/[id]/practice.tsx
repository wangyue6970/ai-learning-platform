import { router, useLocalSearchParams } from 'expo-router';
import { Pressable, Text, View } from 'react-native';
import { initialQuestions } from '../../../data/questions';

export default function PracticeOverviewScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const libraryQuestions = initialQuestions.filter((question) => question.libraryId === id);
  const singleChoiceCount = libraryQuestions.filter((question) => question.type === 'single_choice').length;
  const multipleChoiceCount = libraryQuestions.filter((question) => question.type === 'multiple_choice').length;
  const trueFalseCount = libraryQuestions.filter((question) => question.type === 'true_false').length;

  return (
    <View style={{ flex: 1, padding: 20, paddingTop: 64 }}>
      <Text style={{ fontSize: 26, fontWeight: '700' }}>完整题库练习</Text>
      <Text style={{ marginTop: 12 }}>本次会按题型集中练习。</Text>
      <Pressable onPress={() => router.push(`/library/${id}/practice/single_choice`)}>
        <Text style={{ marginTop: 28 }}>单选题（{singleChoiceCount}）</Text>
      </Pressable>
      <Pressable onPress={() => router.push(`/library/${id}/practice/multiple_choice`)}>
        <Text style={{ marginTop: 16 }}>多选题（{multipleChoiceCount}）</Text>
      </Pressable>
      <Pressable onPress={() => router.push(`/library/${id}/practice/true_false`)}>
        <Text style={{ marginTop: 16 }}>判断题（{trueFalseCount}）</Text>
      </Pressable>
    </View>
  );
}
