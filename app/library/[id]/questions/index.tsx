import { router, useFocusEffect, useLocalSearchParams } from 'expo-router';
import { useCallback, useState } from 'react';
import { FlatList, Pressable, StyleSheet, Text, View } from 'react-native';
import { fetchPracticeQuestions, type PracticeQuestion } from '../../../../services/questionApi';

const questionTypeLabels: Record<PracticeQuestion['type'], string> = {
  single_choice: '单选题',
  multiple_choice: '多选题',
  true_false: '判断题',
};

export default function QuestionListScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const [questions, setQuestions] = useState<PracticeQuestion[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadQuestions = useCallback(async () => {
    if (!id) {
      setError('学习库不存在');
      setIsLoading(false);
      return;
    }

    setIsLoading(true);
    setError(null);
    try {
      setQuestions(await fetchPracticeQuestions(id));
    } catch {
      setError('题目加载失败，请检查后端是否已启动。');
    } finally {
      setIsLoading(false);
    }
  }, [id]);

  useFocusEffect(
    useCallback(() => {
      void loadQuestions();
    }, [loadQuestions])
  );

  if (isLoading) {
    return <View style={styles.centerState}><Text>题目加载中...</Text></View>;
  }

  if (error) {
    return <View style={styles.centerState}><Text>{error}</Text></View>;
  }

  return (
    <View style={styles.screen}>
      <FlatList
        contentContainerStyle={styles.content}
        data={questions}
        keyExtractor={(question) => question.id}
        renderItem={({ item }) => (
          <Pressable
            style={styles.questionCard}
            onPress={() => router.push({
              pathname: '/library/[id]/questions/[questionId]',
              params: { id, questionId: item.id },
            })}>
            <Text style={styles.questionType}>{questionTypeLabels[item.type]}</Text>
            <Text style={styles.questionStem}>{item.stem}</Text>
            <Text style={styles.questionHint}>点击查看、编辑或删除</Text>
          </Pressable>
        )}
        ListEmptyComponent={<Text style={styles.emptyText}>暂无题目，可返回学习库后导入题目。</Text>}
        ListHeaderComponent={
          <>
            <Pressable style={styles.backButton} onPress={() => router.back()}>
              <Text style={styles.backButtonText}>‹ 返回</Text>
            </Pressable>
            <Text style={styles.title}>全部题目</Text>
            <Text style={styles.subtitle}>共 {questions.length} 题。点击题目可查看、编辑或删除。</Text>
          </>
        }
      />
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    backgroundColor: '#F8FAFC',
    flex: 1,
  },
  content: {
    padding: 20,
    paddingTop: 64,
    paddingBottom: 40,
  },
  centerState: {
    alignItems: 'center',
    flex: 1,
    justifyContent: 'center',
    padding: 24,
  },
  backButton: {
    marginBottom: 24,
  },
  backButtonText: {
    color: '#2563EB',
    fontSize: 16,
  },
  title: {
    color: '#0F172A',
    fontSize: 26,
    fontWeight: '700',
  },
  subtitle: {
    color: '#64748B',
    fontSize: 14,
    lineHeight: 21,
    marginTop: 10,
  },
  questionCard: {
    backgroundColor: '#FFFFFF',
    borderColor: '#E2E8F0',
    borderRadius: 12,
    borderWidth: 1,
    marginTop: 12,
    padding: 16,
  },
  questionType: {
    color: '#2563EB',
    fontSize: 13,
    fontWeight: '700',
  },
  questionStem: {
    color: '#0F172A',
    fontSize: 16,
    fontWeight: '700',
    lineHeight: 24,
    marginTop: 8,
  },
  questionHint: {
    color: '#64748B',
    fontSize: 13,
    marginTop: 10,
  },
  emptyText: {
    color: '#64748B',
    fontSize: 15,
    lineHeight: 22,
    marginTop: 28,
  },
});
