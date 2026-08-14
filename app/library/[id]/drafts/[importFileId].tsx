import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Alert, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { confirmImportFileDraft, getImportFileDrafts, QuestionDraft } from '../../../../services/importApi';

const questionTypeText = {
  SINGLE_CHOICE: '单选题',
  MULTIPLE_CHOICE: '多选题',
  TRUE_FALSE: '判断题',
} as const;

export default function QuestionDraftsScreen() {
  const { id, importFileId } = useLocalSearchParams<{ id: string; importFileId: string }>();
  const [drafts, setDrafts] = useState<QuestionDraft[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');
  const [confirmingDraftId, setConfirmingDraftId] = useState<number | null>(null);

  useEffect(() => {
    async function loadDrafts() {
      setIsLoading(true);
      setError('');

      try {
        setDrafts(await getImportFileDrafts(id, importFileId));
      } catch (loadError) {
        setError(loadError instanceof Error ? loadError.message : '读取题目草稿失败，请稍后重试');
      } finally {
        setIsLoading(false);
      }
    }

    void loadDrafts();
  }, [id, importFileId]);

  async function confirmDraft(draft: QuestionDraft) {
    setConfirmingDraftId(draft.id);

    try {
      const confirmedDraft = await confirmImportFileDraft(id, importFileId, draft.id);
      setDrafts((currentDrafts) => currentDrafts.map((item) =>
        item.id === confirmedDraft.id ? confirmedDraft : item
      ));
      Alert.alert('已正式入库', '现在可以回到学习库开始刷这道题。');
    } catch (confirmError) {
      const message = confirmError instanceof Error ? confirmError.message : '确认入库失败，请稍后重试';
      Alert.alert('确认失败', message);
    } finally {
      setConfirmingDraftId(null);
    }
  }

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <Pressable onPress={() => router.back()}>
        <Text style={styles.backText}>返回</Text>
      </Pressable>
      <Text style={styles.title}>确认识别结果</Text>
      <Text style={styles.subtitle}>请先核对题目内容；本页暂时只查看，尚未正式入库。</Text>

      {isLoading && <ActivityIndicator color="#2563EB" size="large" style={styles.loading} />}
      {!!error && <Text style={styles.errorText}>{error}</Text>}
      {!isLoading && !error && drafts.length === 0 && (
        <Text style={styles.emptyText}>还没有可确认的题目草稿。</Text>
      )}

      {drafts.map((draft) => (
        <View key={draft.id} style={styles.draftCard}>
          <Text style={styles.draftNumber}>第 {draft.sortOrder} 题 · {questionTypeText[draft.questionType]}</Text>
          <Text style={styles.stem}>{draft.stem}</Text>

          {draft.options.map((option) => (
            <Text key={option.optionKey} style={styles.option}>
              {option.optionKey}. {option.content || '（选项内容为空）'}
            </Text>
          ))}

          <Text style={styles.label}>识别答案</Text>
          <Text style={styles.value}>{draft.correctAnswer.length > 0 ? draft.correctAnswer.join('、') : '未识别到答案'}</Text>
          {!!draft.explanation && <Text style={styles.explanation}>解析：{draft.explanation}</Text>}
          {draft.status === 'CONFIRMED' ? (
            <Text style={styles.confirmedText}>已正式入库</Text>
          ) : (
            <View style={styles.buttonRow}>
              <Pressable
                style={styles.editButton}
                onPress={() => router.push({
                  pathname: '/library/[id]/drafts/[importFileId]/[draftId]',
                  params: { id, importFileId, draftId: String(draft.id) },
                })}
              >
                <Text style={styles.editButtonText}>编辑草稿</Text>
              </Pressable>
              <Pressable
                style={[styles.confirmButton, confirmingDraftId === draft.id && styles.confirmButtonDisabled]}
                onPress={() => void confirmDraft(draft)}
                disabled={confirmingDraftId === draft.id}
              >
                <Text style={styles.confirmButtonText}>
                  {confirmingDraftId === draft.id ? '正在入库…' : '确认入库'}
                </Text>
              </Pressable>
            </View>
          )}
        </View>
      ))}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { backgroundColor: '#F8FAFC', flexGrow: 1, padding: 20, paddingBottom: 40, paddingTop: 64 },
  backText: { color: '#2563EB', fontSize: 16 },
  title: { color: '#0F172A', fontSize: 28, fontWeight: '700', marginTop: 24 },
  subtitle: { color: '#64748B', fontSize: 15, lineHeight: 22, marginTop: 10 },
  loading: { marginTop: 42 },
  errorText: { color: '#B91C1C', marginTop: 28 },
  emptyText: { color: '#64748B', marginTop: 28 },
  draftCard: { backgroundColor: '#FFFFFF', borderColor: '#E2E8F0', borderRadius: 12, borderWidth: 1, marginTop: 18, padding: 16 },
  draftNumber: { color: '#2563EB', fontSize: 14, fontWeight: '700' },
  stem: { color: '#0F172A', fontSize: 17, fontWeight: '700', lineHeight: 25, marginTop: 12 },
  option: { color: '#334155', fontSize: 15, lineHeight: 22, marginTop: 10 },
  label: { color: '#64748B', fontSize: 13, marginTop: 18 },
  value: { color: '#0F172A', fontSize: 16, fontWeight: '700', marginTop: 5 },
  explanation: { color: '#475569', fontSize: 14, lineHeight: 21, marginTop: 14 },
  buttonRow: { flexDirection: 'row', gap: 10, marginTop: 18 },
  editButton: { alignItems: 'center', borderColor: '#2563EB', borderRadius: 8, borderWidth: 1, flex: 1, paddingVertical: 10 },
  editButtonText: { color: '#2563EB', fontSize: 14, fontWeight: '700' },
  confirmButton: { alignItems: 'center', backgroundColor: '#2563EB', borderRadius: 8, flex: 1, paddingVertical: 10 },
  confirmButtonDisabled: { backgroundColor: '#93C5FD' },
  confirmButtonText: { color: '#FFFFFF', fontSize: 14, fontWeight: '700' },
  confirmedText: { color: '#15803D', fontSize: 15, fontWeight: '700', marginTop: 20 },
});
