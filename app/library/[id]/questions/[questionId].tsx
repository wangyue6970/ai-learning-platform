import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { fetchQuestionDetail, type EditableQuestion } from '../../../../services/questionApi';

const questionTypeLabels = {
  single_choice: '单选题',
  multiple_choice: '多选题',
  true_false: '判断题',
};

export default function QuestionDetailScreen() {
  const { questionId } = useLocalSearchParams<{ questionId: string }>();
  const [question, setQuestion] = useState<EditableQuestion | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    async function loadQuestion() {
      try {
        setQuestion(await fetchQuestionDetail(questionId));
      } catch {
        setError('题目详情加载失败，请检查后端是否已启动。');
      }
    }

    void loadQuestion();
  }, [questionId]);

  if (error) {
    return <View style={styles.centerState}><Text>{error}</Text></View>;
  }

  if (!question) {
    return <View style={styles.centerState}><Text>题目详情加载中...</Text></View>;
  }

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <Pressable onPress={() => router.back()}>
        <Text style={styles.backText}>‹ 返回学习库</Text>
      </Pressable>
      <Text style={styles.type}>{questionTypeLabels[question.type]}</Text>
      <Text style={styles.stem}>{question.stem}</Text>

      {question.options.map((option) => (
        <View key={option.id} style={styles.optionCard}>
          <Text style={styles.optionText}>{option.id}. {option.text}</Text>
        </View>
      ))}

      <Text style={styles.sectionTitle}>正确答案</Text>
      <Text style={styles.answerText}>{question.correctAnswer.join('、')}</Text>
      <Text style={styles.sectionTitle}>解析</Text>
      <Text style={styles.explanationText}>{question.explanation || '暂无解析'}</Text>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { backgroundColor: '#FFFFFF', flexGrow: 1, padding: 20, paddingTop: 64 },
  centerState: { alignItems: 'center', flex: 1, justifyContent: 'center', padding: 24 },
  backText: { color: '#2563EB', fontSize: 16, marginBottom: 26 },
  type: { color: '#2563EB', fontSize: 14, fontWeight: '700' },
  stem: { color: '#0F172A', fontSize: 20, fontWeight: '700', lineHeight: 30, marginTop: 10 },
  optionCard: { borderColor: '#CBD5E1', borderRadius: 10, borderWidth: 1, marginTop: 12, padding: 14 },
  optionText: { color: '#334155', fontSize: 16, lineHeight: 24 },
  sectionTitle: { color: '#0F172A', fontSize: 16, fontWeight: '700', marginTop: 28 },
  answerText: { color: '#16A34A', fontSize: 18, fontWeight: '700', marginTop: 8 },
  explanationText: { color: '#334155', fontSize: 16, lineHeight: 25, marginTop: 8 },
});
