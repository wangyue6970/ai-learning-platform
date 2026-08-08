import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useState } from 'react';
import { Pressable, Text, View } from 'react-native';
import { fetchPracticeQuestions, fetchWrongQuestions, type PracticeQuestion } from '../../../services/questionApi';

export default function PracticeOverviewScreen() {
  const { id, mode } = useLocalSearchParams<{ id: string; mode?: string }>();
  const isWrongPractice = mode === 'wrong';
  const [questions, setQuestions] = useState<PracticeQuestion[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    async function loadQuestions() {
      if (!id) {
        setError('学习库不存在');
        setIsLoading(false);
        return;
      }

      setIsLoading(true);
      setError(null);
      try {
        const loadedQuestions = isWrongPractice
          ? await fetchWrongQuestions(id)
          : await fetchPracticeQuestions(id);
        setQuestions(loadedQuestions);
      } catch {
        setError('无法加载题目，请检查后端是否已启动');
      } finally {
        setIsLoading(false);
      }
    }

    void loadQuestions();
  }, [id, isWrongPractice]);

  const singleChoiceCount = questions.filter((question) => question.type === 'single_choice').length;
  const multipleChoiceCount = questions.filter((question) => question.type === 'multiple_choice').length;
  const trueFalseCount = questions.filter((question) => question.type === 'true_false').length;

  if (isLoading) {
    return <Text>题目加载中...</Text>;
  }

  if (error) {
    return <Text>{error}</Text>;
  }

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
