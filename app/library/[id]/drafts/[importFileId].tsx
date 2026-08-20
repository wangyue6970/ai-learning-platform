import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { confirmImportFileDraft, discardImportFileDraft, getImportFileDrafts, QuestionDraft } from '../../../../services/importApi';
import { ui } from '../../../../constants/ui';
import { useDialog } from '../../../../components/AppDialog';

const questionTypeText = {
  SINGLE_CHOICE: '单选题',
  MULTIPLE_CHOICE: '多选题',
  TRUE_FALSE: '判断题',
} as const;

export default function QuestionDraftsScreen() {
  const { id, importFileId } = useLocalSearchParams<{ id: string; importFileId: string }>();
  const { showDialog } = useDialog();
  const [drafts, setDrafts] = useState<QuestionDraft[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');
  const [actingDraftId, setActingDraftId] = useState<number | null>(null);

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
    setActingDraftId(draft.id);

    try {
      const confirmedDraft = await confirmImportFileDraft(id, importFileId, draft.id);
      setDrafts((currentDrafts) => currentDrafts.map((item) =>
        item.id === confirmedDraft.id ? confirmedDraft : item
      ));
      showDialog({ title: '已正式入库', message: '现在可以回到学习库开始刷这道题。', tone: 'success' });
    } catch (confirmError) {
      const message = confirmError instanceof Error ? confirmError.message : '确认入库失败，请稍后重试';
      showDialog({ title: '确认失败', message, tone: 'danger' });
    } finally {
      setActingDraftId(null);
    }
  }

  async function discardDraft(draft: QuestionDraft) {
    setActingDraftId(draft.id);

    try {
      await discardImportFileDraft(id, importFileId, draft.id);
      setDrafts((currentDrafts) => currentDrafts.filter((item) => item.id !== draft.id));
    } catch (discardError) {
      const message = discardError instanceof Error ? discardError.message : '不入库失败，请稍后重试';
      showDialog({ title: '操作失败', message, tone: 'danger' });
    } finally {
      setActingDraftId(null);
    }
  }

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <Pressable onPress={() => router.back()}>
        <Text style={styles.backText}>‹ 返回</Text>
      </Pressable>
      <Text style={styles.title}>确认识别结果</Text>
      <Text style={styles.subtitle}>先核对识别内容，需要时可编辑；确认后才会进入正式题库。</Text>

      {isLoading && <ActivityIndicator color="#2563EB" size="large" style={styles.loading} />}
      {!!error && <Text style={styles.errorText}>{error}</Text>}
      {!isLoading && !error && drafts.length === 0 && (
        <Text style={styles.emptyText}>还没有可确认的题目草稿。</Text>
      )}

      {drafts.map((draft) => (
          <View key={draft.id} style={styles.draftCard}>
          <View style={styles.draftHeader}>
            <Text style={styles.draftNumber}>第 {draft.sortOrder} 题</Text>
            <Text style={[styles.typeBadge, draft.status === 'NEEDS_REVIEW' && styles.reviewTypeBadge]}>
              {draft.status === 'NEEDS_REVIEW' ? '需要修正' : questionTypeText[draft.questionType]}
            </Text>
          </View>
          <Text style={styles.stem}>{draft.stem}</Text>
          {!!draft.issueReason && <Text style={styles.issueText}>问题：{draft.issueReason}</Text>}

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
          ) : draft.status === 'NEEDS_REVIEW' ? (
            <Pressable
              style={styles.editButton}
              onPress={() => router.push({
                pathname: '/library/[id]/drafts/[importFileId]/[draftId]',
                params: { id, importFileId, draftId: String(draft.id) },
              })}>
              <Text style={styles.editButtonText}>去修正这道题</Text>
            </Pressable>
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
                style={[styles.confirmButton, actingDraftId === draft.id && styles.confirmButtonDisabled]}
                onPress={() => void confirmDraft(draft)}
                disabled={actingDraftId === draft.id}
              >
                <Text style={styles.confirmButtonText}>
                  {actingDraftId === draft.id ? '正在处理…' : '确认入库'}
                </Text>
              </Pressable>
            </View>
          )}
          {draft.status !== 'CONFIRMED' && (
            <Pressable
              disabled={actingDraftId === draft.id}
              style={styles.discardButton}
              onPress={() => void discardDraft(draft)}>
              <Text style={styles.discardButtonText}>不入库</Text>
            </Pressable>
          )}
        </View>
      ))}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { backgroundColor: ui.colors.background, flexGrow: 1, paddingHorizontal: 18, paddingBottom: 48, paddingTop: 54 },
  backText: { color: ui.colors.primary, fontSize: 16, fontWeight: '700' },
  title: { color: ui.colors.text, fontSize: 23, fontWeight: '800', marginTop: 22 },
  subtitle: { color: ui.colors.mutedText, fontSize: 14, lineHeight: 21, marginTop: 10 },
  loading: { marginTop: 42 },
  errorText: { color: ui.colors.danger, marginTop: 28 },
  emptyText: { color: ui.colors.mutedText, marginTop: 28 },
  draftCard: { backgroundColor: ui.colors.surface, borderColor: '#E8EDF5', borderRadius: 14, borderWidth: 1, marginTop: 14, padding: 14, ...ui.subtleShadow },
  draftHeader: { alignItems: 'center', flexDirection: 'row', justifyContent: 'space-between' },
  draftNumber: { color: ui.colors.mutedText, fontSize: 12, fontWeight: '800' },
  typeBadge: { backgroundColor: ui.colors.primarySoft, borderRadius: 8, color: ui.colors.primary, fontSize: 12, fontWeight: '800', paddingHorizontal: 8, paddingVertical: 4 },
  reviewTypeBadge: { backgroundColor: '#FFF1D9', color: '#A85C00' },
  stem: { color: ui.colors.text, fontSize: 16, fontWeight: '800', lineHeight: 24, marginTop: 11 },
  option: { color: ui.colors.mutedText, fontSize: 14, lineHeight: 21, marginTop: 9 },
  label: { color: ui.colors.mutedText, fontSize: 12, fontWeight: '800', marginTop: 16 },
  value: { color: ui.colors.text, fontSize: 15, fontWeight: '800', marginTop: 5 },
  explanation: { color: ui.colors.mutedText, fontSize: 13, lineHeight: 20, marginTop: 12 },
  issueText: { backgroundColor: '#FFF8EB', borderRadius: 9, color: '#A85C00', fontSize: 12, fontWeight: '700', lineHeight: 18, marginTop: 12, padding: 10 },
  buttonRow: { flexDirection: 'row', gap: 9, marginTop: 17 },
  editButton: { alignItems: 'center', borderColor: '#B7CDFC', borderRadius: 10, borderWidth: 1, flex: 1, paddingVertical: 11 },
  editButtonText: { color: ui.colors.primary, fontSize: 14, fontWeight: '800' },
  confirmButton: { alignItems: 'center', backgroundColor: ui.colors.primary, borderRadius: 10, flex: 1, paddingVertical: 11 },
  confirmButtonDisabled: { backgroundColor: ui.colors.disabled },
  confirmButtonText: { color: '#FFFFFF', fontSize: 14, fontWeight: '800' },
  discardButton: { alignItems: 'center', marginTop: 12, paddingVertical: 4 },
  discardButtonText: { color: ui.colors.danger, fontSize: 14, fontWeight: '800' },
  confirmedText: { color: ui.colors.success, fontSize: 15, fontWeight: '800', marginTop: 20 },
});
