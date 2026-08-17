import { router, useFocusEffect, useLocalSearchParams } from 'expo-router';
import { useCallback, useState } from 'react';
import { ActivityIndicator, Alert, FlatList, Pressable, StyleSheet, Text, View } from 'react-native';
import {
  confirmAllImportBatchDrafts,
  confirmImportFileDraft,
  discardImportFileDraft,
  getImportBatchDrafts,
  QuestionDraft,
} from '../../../../services/importApi';

const questionTypeText = {
  SINGLE_CHOICE: '单选题',
  MULTIPLE_CHOICE: '多选题',
  TRUE_FALSE: '判断题',
} as const;

export default function BatchQuestionDraftsScreen() {
  const { id, importBatchId } = useLocalSearchParams<{ id: string; importBatchId: string }>();
  const [drafts, setDrafts] = useState<QuestionDraft[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');
  const [actingDraftId, setActingDraftId] = useState<number | null>(null);
  const [isConfirmingAll, setIsConfirmingAll] = useState(false);

  const loadDrafts = useCallback(async () => {
    setIsLoading(true);
    setError('');

    try {
      setDrafts(await getImportBatchDrafts(id, importBatchId));
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : '读取本批次草稿失败，请稍后重试');
    } finally {
      setIsLoading(false);
    }
  }, [id, importBatchId]);

  useFocusEffect(
    useCallback(() => {
      void loadDrafts();
    }, [loadDrafts])
  );

  async function confirmDraft(draft: QuestionDraft) {
    if (isConfirmingAll) {
      return;
    }
    setActingDraftId(draft.id);

    try {
      const confirmedDraft = await confirmImportFileDraft(id, String(draft.importFileId), draft.id);
      setDrafts((currentDrafts) => currentDrafts.map((item) =>
        item.id === confirmedDraft.id ? confirmedDraft : item
      ));
    } catch (confirmError) {
      const message = confirmError instanceof Error ? confirmError.message : '确认入库失败，请稍后重试';
      Alert.alert('确认失败', message);
    } finally {
      setActingDraftId(null);
    }
  }

  async function discardDraft(draft: QuestionDraft) {
    if (isConfirmingAll) {
      return;
    }
    setActingDraftId(draft.id);

    try {
      await discardImportFileDraft(id, String(draft.importFileId), draft.id);
      setDrafts((currentDrafts) => currentDrafts.filter((item) => item.id !== draft.id));
    } catch (discardError) {
      const message = discardError instanceof Error ? discardError.message : '不入库失败，请稍后重试';
      Alert.alert('不入库失败', message);
    } finally {
      setActingDraftId(null);
    }
  }

  async function confirmAllDrafts() {
    if (isConfirmingAll || waitingConfirmationCount === 0) {
      return;
    }

    setIsConfirmingAll(true);
    try {
      const result = await confirmAllImportBatchDrafts(id, importBatchId);
      await loadDrafts();
      const failedCount = result.failedDrafts.length;
      Alert.alert(
        failedCount === 0 ? '批量入库完成' : '部分题目需要补充',
        failedCount === 0
          ? `已入库 ${result.confirmedCount} 道题。`
          : `已入库 ${result.confirmedCount} 道题；${failedCount} 道仍是草稿，请补充答案或检查内容后再确认。`
      );
    } catch (confirmError) {
      const message = confirmError instanceof Error ? confirmError.message : '批量确认入库失败，请稍后重试';
      Alert.alert('批量确认失败', message);
    } finally {
      setIsConfirmingAll(false);
    }
  }

  const waitingConfirmationCount = drafts.filter((draft) => draft.status !== 'CONFIRMED').length;

  return (
    <FlatList
      contentContainerStyle={styles.container}
      data={drafts}
      keyExtractor={(draft) => String(draft.id)}
      ListHeaderComponent={
        <View>
          <View style={styles.topActionRow}>
            <Pressable onPress={() => router.back()}>
              <Text style={styles.backText}>返回</Text>
            </Pressable>
            <Pressable
              disabled={waitingConfirmationCount === 0 || isConfirmingAll}
              style={[
                styles.confirmAllButton,
                (waitingConfirmationCount === 0 || isConfirmingAll) && styles.confirmAllButtonDisabled,
              ]}
              onPress={() => void confirmAllDrafts()}
            >
              <Text style={styles.confirmAllButtonText}>
                {isConfirmingAll ? '正在入库…' : `全部入库（${waitingConfirmationCount}）`}
              </Text>
            </Pressable>
          </View>
          <Text style={styles.title}>本批次题目草稿</Text>
          <Text style={styles.subtitle}>
            共 {drafts.length} 道；待确认 {waitingConfirmationCount} 道。确认前可以修改，确认后才会进入正式题库。
          </Text>
          {isLoading && <ActivityIndicator color="#2563EB" size="large" style={styles.loading} />}
          {!!error && <Text style={styles.errorText}>{error}</Text>}
          {!isLoading && !error && drafts.length === 0 && (
            <Text style={styles.emptyText}>这个批次暂时没有可查看的草稿。</Text>
          )}
        </View>
      }
      renderItem={({ item: draft, index }) => (
        <View style={styles.draftCard}>
          <Text style={styles.draftNumber}>第 {index + 1} 题 · {questionTypeText[draft.questionType]}</Text>
          <Text style={styles.stem}>{draft.stem}</Text>
          <Text style={styles.answerText}>
            识别答案：{draft.correctAnswer.length > 0 ? draft.correctAnswer.join('、') : '未识别到答案'}
          </Text>
          {draft.status === 'CONFIRMED' ? (
            <Text style={styles.confirmedText}>已正式入库</Text>
          ) : (
            <View style={styles.buttonRow}>
              <Pressable
                style={styles.editButton}
                onPress={() => router.push({
                  pathname: '/library/[id]/drafts/[importFileId]/[draftId]',
                  params: { id, importFileId: String(draft.importFileId), draftId: String(draft.id) },
                })}>
                <Text style={styles.editButtonText}>编辑草稿</Text>
              </Pressable>
              <Pressable
                disabled={actingDraftId === draft.id || isConfirmingAll}
                style={[styles.confirmButton, (actingDraftId === draft.id || isConfirmingAll) && styles.confirmButtonDisabled]}
                onPress={() => void confirmDraft(draft)}>
                <Text style={styles.confirmButtonText}>
                  {actingDraftId === draft.id ? '正在处理…' : '确认入库'}
                </Text>
              </Pressable>
            </View>
          )}
          {draft.status !== 'CONFIRMED' && (
            <Pressable
              disabled={actingDraftId === draft.id || isConfirmingAll}
              style={styles.discardButton}
              onPress={() => void discardDraft(draft)}>
              <Text style={styles.discardButtonText}>不入库</Text>
            </Pressable>
          )}
        </View>
      )}
    />
  );
}

const styles = StyleSheet.create({
  container: { backgroundColor: '#F8FAFC', flexGrow: 1, padding: 20, paddingBottom: 40, paddingTop: 64 },
  topActionRow: { alignItems: 'center', flexDirection: 'row', gap: 14 },
  backText: { color: '#2563EB', fontSize: 16 },
  confirmAllButton: { backgroundColor: '#2563EB', borderRadius: 8, paddingHorizontal: 12, paddingVertical: 8 },
  confirmAllButtonDisabled: { backgroundColor: '#93C5FD' },
  confirmAllButtonText: { color: '#FFFFFF', fontSize: 13, fontWeight: '700' },
  title: { color: '#0F172A', fontSize: 28, fontWeight: '700', marginTop: 24 },
  subtitle: { color: '#64748B', fontSize: 15, lineHeight: 22, marginTop: 10 },
  loading: { marginTop: 30 },
  errorText: { color: '#B91C1C', fontSize: 15, marginTop: 24 },
  emptyText: { color: '#64748B', fontSize: 15, marginTop: 24 },
  draftCard: { backgroundColor: '#FFFFFF', borderColor: '#E2E8F0', borderRadius: 12, borderWidth: 1, marginTop: 16, padding: 16 },
  draftNumber: { color: '#2563EB', fontSize: 14, fontWeight: '700' },
  stem: { color: '#0F172A', fontSize: 17, fontWeight: '700', lineHeight: 25, marginTop: 10 },
  answerText: { color: '#475569', fontSize: 14, marginTop: 14 },
  confirmedText: { color: '#15803D', fontSize: 15, fontWeight: '700', marginTop: 18 },
  buttonRow: { flexDirection: 'row', gap: 10, marginTop: 18 },
  editButton: { alignItems: 'center', borderColor: '#2563EB', borderRadius: 8, borderWidth: 1, flex: 1, paddingVertical: 10 },
  editButtonText: { color: '#2563EB', fontSize: 14, fontWeight: '700' },
  confirmButton: { alignItems: 'center', backgroundColor: '#2563EB', borderRadius: 8, flex: 1, paddingVertical: 10 },
  confirmButtonDisabled: { backgroundColor: '#93C5FD' },
  confirmButtonText: { color: '#FFFFFF', fontSize: 14, fontWeight: '700' },
  discardButton: { alignItems: 'center', marginTop: 14, paddingVertical: 4 },
  discardButtonText: { color: '#B91C1C', fontSize: 14, fontWeight: '700' },
});
