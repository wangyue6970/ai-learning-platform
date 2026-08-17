import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { fetchPracticeQuestions, fetchWrongQuestions, type PracticeQuestion } from '../../../services/questionApi';
import { ui } from '../../../constants/ui';

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
    return <View style={styles.centerState}><Text style={styles.stateText}>题目加载中...</Text></View>;
  }

  if (error) {
    return <View style={styles.centerState}><Text style={styles.stateText}>{error}</Text></View>;
  }

  return (
    <View style={styles.screen}>
      <Pressable style={styles.backButton} onPress={() => router.back()}>
        <Text style={styles.backText}>‹ 返回</Text>
      </Pressable>
      <Text style={styles.title}>
        {isWrongPractice ? '错题集练习' : '完整题库练习'}
      </Text>
      <Text style={styles.subtitle}>本次会将相同类型的题集中在一起练习。</Text>
      <Pressable
        style={styles.practiceCard}
        onPress={() =>
          router.push({
            pathname: '/library/[id]/practice/[type]',
            params: { id, type: 'single_choice', mode },
          })
        }>
        <View style={[styles.typeIcon, styles.singleIcon]}><Text style={styles.typeIconText}>A</Text></View>
        <View style={styles.cardInfo}><Text style={styles.cardTitle}>单选题</Text><Text style={styles.cardMeta}>共 {singleChoiceCount} 题</Text></View>
        <Text style={styles.chevron}>›</Text>
      </Pressable>
      <Pressable
        style={styles.practiceCard}
        onPress={() =>
          router.push({
            pathname: '/library/[id]/practice/[type]',
            params: { id, type: 'multiple_choice', mode },
          })
        }>
        <View style={[styles.typeIcon, styles.multipleIcon]}><Text style={styles.typeIconText}>☷</Text></View>
        <View style={styles.cardInfo}><Text style={styles.cardTitle}>多选题</Text><Text style={styles.cardMeta}>共 {multipleChoiceCount} 题</Text></View>
        <Text style={styles.chevron}>›</Text>
      </Pressable>
      <Pressable
        style={styles.practiceCard}
        onPress={() =>
          router.push({
            pathname: '/library/[id]/practice/[type]',
            params: { id, type: 'true_false', mode },
          })
        }>
        <View style={[styles.typeIcon, styles.trueFalseIcon]}><Text style={styles.typeIconText}>✓</Text></View>
        <View style={styles.cardInfo}><Text style={styles.cardTitle}>判断题</Text><Text style={styles.cardMeta}>共 {trueFalseCount} 题</Text></View>
        <Text style={styles.chevron}>›</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { backgroundColor: ui.colors.background, flex: 1, padding: 20, paddingTop: 58 },
  centerState: { alignItems: 'center', backgroundColor: ui.colors.background, flex: 1, justifyContent: 'center', padding: 24 },
  stateText: { color: ui.colors.mutedText, fontSize: 15 },
  backButton: { alignSelf: 'flex-start', marginBottom: 26 },
  backText: { color: ui.colors.primary, fontSize: 16, fontWeight: '700' },
  title: { color: ui.colors.text, fontSize: 29, fontWeight: '800', letterSpacing: -0.6 },
  subtitle: { color: ui.colors.mutedText, fontSize: 15, lineHeight: 22, marginTop: 10 },
  practiceCard: { alignItems: 'center', backgroundColor: ui.colors.surface, borderColor: ui.colors.border, borderRadius: ui.radius.card, borderWidth: 1, flexDirection: 'row', marginTop: 14, padding: 16, ...ui.subtleShadow },
  typeIcon: { alignItems: 'center', borderRadius: 14, height: 48, justifyContent: 'center', width: 48 },
  singleIcon: { backgroundColor: ui.colors.primarySoft },
  multipleIcon: { backgroundColor: '#F1ECFF' },
  trueFalseIcon: { backgroundColor: ui.colors.successSoft },
  typeIconText: { color: ui.colors.primary, fontSize: 19, fontWeight: '800' },
  cardInfo: { flex: 1, marginLeft: 14 },
  cardTitle: { color: ui.colors.text, fontSize: 17, fontWeight: '800' },
  cardMeta: { color: ui.colors.mutedText, fontSize: 13, marginTop: 6 },
  chevron: { color: ui.colors.disabled, fontSize: 28, lineHeight: 30 },
});
