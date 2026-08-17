import { router, useFocusEffect, useLocalSearchParams } from 'expo-router';
import { useCallback, useState } from 'react';
import { FlatList, Pressable, StyleSheet, Text, View } from 'react-native';
import { fetchPracticeQuestions, type PracticeQuestion } from '../../../../services/questionApi';
import { ui } from '../../../../constants/ui';

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
            <View style={styles.questionTopRow}><Text style={styles.questionType}>{questionTypeLabels[item.type]}</Text><Text style={styles.chevron}>›</Text></View>
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
    backgroundColor: ui.colors.background,
    flex: 1,
  },
  content: {
    padding: 20,
    paddingTop: 64,
    paddingBottom: 48,
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
    color: ui.colors.primary,
    fontSize: 16,
  },
  title: {
    color: ui.colors.text,
    fontSize: 29,
    fontWeight: '800',
  },
  subtitle: {
    color: ui.colors.mutedText,
    fontSize: 14,
    lineHeight: 21,
    marginTop: 10,
  },
  questionCard: {
    backgroundColor: ui.colors.surface,
    borderColor: ui.colors.border,
    borderRadius: ui.radius.card,
    borderWidth: 1,
    marginTop: 12,
    padding: 16,
    ...ui.subtleShadow,
  },
  questionTopRow: { alignItems: 'center', flexDirection: 'row', justifyContent: 'space-between' },
  questionType: {
    color: ui.colors.primary,
    fontSize: 13,
    fontWeight: '700',
  },
  questionStem: {
    color: ui.colors.text,
    fontSize: 16,
    fontWeight: '700',
    lineHeight: 24,
    marginTop: 8,
  },
  questionHint: {
    color: ui.colors.mutedText,
    fontSize: 13,
    marginTop: 10,
  },
  emptyText: {
    color: ui.colors.mutedText,
    fontSize: 15,
    lineHeight: 22,
    marginTop: 28,
  },
  chevron: { color: ui.colors.disabled, fontSize: 26, lineHeight: 28 },
});
