import { router, useFocusEffect, useLocalSearchParams } from 'expo-router';
import { useCallback, useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, StyleSheet, Text, View } from 'react-native';
import {
  confirmAllImportBatchDrafts,
  confirmImportFileDraft,
  discardImportFileDraft,
  getImportBatchDrafts,
  QuestionDraft,
} from '../../../../services/importApi';
import { ui } from '../../../../constants/ui';
import { useDialog } from '../../../../components/AppDialog';

const questionTypeText = {
  SINGLE_CHOICE: '单选题',
  MULTIPLE_CHOICE: '多选题',
  TRUE_FALSE: '判断题',
} as const;

export default function BatchQuestionDraftsScreen() {
  const { id, importBatchId, filter } = useLocalSearchParams<{
    id: string;
    importBatchId: string;
    filter?: 'needs_review';
  }>();
  const { showDialog } = useDialog();
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
      showDialog({ title: '确认失败', message, tone: 'danger' });
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
      showDialog({ title: '操作失败', message, tone: 'danger' });
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
      showDialog({
        title: failedCount === 0 ? '批量入库完成' : '部分题目需要补充',
        message: failedCount === 0
          ? `已入库 ${result.confirmedCount} 道题。`
          : `已入库 ${result.confirmedCount} 道题；${failedCount} 道仍是草稿，请补充答案或检查内容后再确认。`,
        tone: failedCount === 0 ? 'success' : 'warning',
      });
    } catch (confirmError) {
      const message = confirmError instanceof Error ? confirmError.message : '批量确认入库失败，请稍后重试';
      showDialog({ title: '批量确认失败', message, tone: 'danger' });
    } finally {
      setIsConfirmingAll(false);
    }
  }

  const isReviewOnly = filter === 'needs_review';
  const visibleDrafts = isReviewOnly ? drafts.filter((draft) => draft.status === 'NEEDS_REVIEW') : drafts;
  const needsReviewCount = drafts.filter((draft) => draft.status === 'NEEDS_REVIEW').length;
  const waitingConfirmationCount = drafts.filter((draft) => draft.status === 'WAITING_CONFIRMATION').length;
  const confirmedCount = drafts.filter((draft) => draft.status === 'CONFIRMED').length;
  const confirmationProgress = drafts.length === 0 ? 0 : (confirmedCount / drafts.length) * 100;

  return (
    <FlatList
      contentContainerStyle={styles.container}
      data={visibleDrafts}
      keyExtractor={(draft) => String(draft.id)}
      ListHeaderComponent={
        <View>
          <View style={styles.topActionRow}>
            <Pressable onPress={() => router.back()}>
              <Text style={styles.backText}>返回</Text>
            </Pressable>
            {!isReviewOnly && (
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
            )}
          </View>
          <View style={styles.titleRow}>
            <Text style={styles.title}>{isReviewOnly ? '待修正题目' : '确认识别结果'}</Text>
            <Text style={[styles.progressBadge, isReviewOnly && styles.reviewBadge]}>
              {isReviewOnly ? `待修正 ${needsReviewCount}` : `待确认 ${waitingConfirmationCount}`}
            </Text>
          </View>
          <Text style={styles.subtitle}>
            {isReviewOnly
              ? '这些题目需要先补充或修正。保存修改后才会回到待确认列表。'
              : `共识别出 ${drafts.length} 道题。可逐题修改；确认后才会进入正式题库。`}
          </Text>
          <View style={styles.progressTrack}>
            <View style={[styles.progressFill, { width: `${confirmationProgress}%` }]} />
          </View>
          <Text style={styles.progressHint}>已确认 {confirmedCount} / {drafts.length} 题</Text>
          {isLoading && <ActivityIndicator color="#2563EB" size="large" style={styles.loading} />}
          {!!error && <Text style={styles.errorText}>{error}</Text>}
          {!isLoading && !error && visibleDrafts.length === 0 && (
            <Text style={styles.emptyText}>{isReviewOnly ? '没有待修正题目。' : '这个批次暂时没有可查看的草稿。'}</Text>
          )}
        </View>
      }
      renderItem={({ item: draft, index }) => (
        <View style={styles.draftCard}>
          <View style={styles.draftHeader}>
            <Text style={styles.draftNumber}>第 {draft.sortOrder || index + 1} 题</Text>
            <Text style={[styles.typeBadge, draft.status === 'NEEDS_REVIEW' && styles.reviewTypeBadge]}>
              {draft.status === 'NEEDS_REVIEW' ? '需要修正' : questionTypeText[draft.questionType]}
            </Text>
          </View>
          <Text style={styles.stem}>{draft.stem}</Text>
          {!!draft.issueReason && <Text style={styles.issueText}>问题：{draft.issueReason}</Text>}
          <Text style={styles.answerText}>
            识别答案：{draft.correctAnswer.length > 0 ? draft.correctAnswer.join('、') : '未识别到答案'}
          </Text>
          {draft.status === 'CONFIRMED' ? (
            <Text style={styles.confirmedText}>已正式入库</Text>
          ) : draft.status === 'NEEDS_REVIEW' ? (
            <Pressable
              style={styles.editButton}
              onPress={() => router.push({
                pathname: '/library/[id]/drafts/[importFileId]/[draftId]',
                params: { id, importFileId: String(draft.importFileId), draftId: String(draft.id), returnToBatchId: importBatchId },
              })}>
              <Text style={styles.editButtonText}>去修正这道题</Text>
            </Pressable>
          ) : (
            <View style={styles.buttonRow}>
              <Pressable
                style={styles.editButton}
                onPress={() => router.push({
                  pathname: '/library/[id]/drafts/[importFileId]/[draftId]',
                  params: { id, importFileId: String(draft.importFileId), draftId: String(draft.id), returnToBatchId: importBatchId },
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
  container: { backgroundColor: ui.colors.background, flexGrow: 1, paddingHorizontal: 18, paddingBottom: 48, paddingTop: 54 },
  topActionRow: { alignItems: 'center', flexDirection: 'row', justifyContent: 'space-between' },
  backText: { color: ui.colors.primary, fontSize: 15, fontWeight: '800' },
  confirmAllButton: { backgroundColor: ui.colors.primary, borderRadius: 11, paddingHorizontal: 13, paddingVertical: 10, ...ui.shadow },
  confirmAllButtonDisabled: { backgroundColor: ui.colors.disabled },
  confirmAllButtonText: { color: '#FFFFFF', fontSize: 13, fontWeight: '800' },
  titleRow: { alignItems: 'center', flexDirection: 'row', justifyContent: 'space-between', marginTop: 23 },
  title: { color: ui.colors.text, fontSize: 23, fontWeight: '800', letterSpacing: -0.6 },
  progressBadge: { backgroundColor: ui.colors.primarySoft, borderRadius: 9, color: ui.colors.primary, fontSize: 12, fontWeight: '800', paddingHorizontal: 9, paddingVertical: 5 },
  reviewBadge: { backgroundColor: '#FFF1D9', color: '#A85C00' },
  subtitle: { color: ui.colors.mutedText, fontSize: 14, lineHeight: 21, marginTop: 10 },
  progressTrack: { backgroundColor: '#E8EDF5', borderRadius: 4, height: 4, marginTop: 14, overflow: 'hidden' },
  progressFill: { backgroundColor: ui.colors.primary, borderRadius: 4, height: '100%' },
  progressHint: { color: ui.colors.mutedText, fontSize: 11, marginTop: 7 },
  loading: { marginTop: 30 },
  errorText: { color: ui.colors.danger, fontSize: 15, marginTop: 24 },
  emptyText: { color: ui.colors.mutedText, fontSize: 15, marginTop: 24 },
  draftCard: { backgroundColor: ui.colors.surface, borderColor: '#E8EDF5', borderRadius: 14, borderWidth: 1, marginTop: 12, padding: 14, ...ui.subtleShadow },
  draftHeader: { alignItems: 'center', flexDirection: 'row', justifyContent: 'space-between' },
  draftNumber: { color: ui.colors.mutedText, fontSize: 13, fontWeight: '800' },
  typeBadge: { backgroundColor: ui.colors.primarySoft, borderRadius: 8, color: ui.colors.primary, fontSize: 12, fontWeight: '800', paddingHorizontal: 8, paddingVertical: 4 },
  reviewTypeBadge: { backgroundColor: '#FFF1D9', color: '#A85C00' },
  stem: { color: ui.colors.text, fontSize: 16, fontWeight: '800', lineHeight: 24, marginTop: 11 },
  answerText: { color: ui.colors.mutedText, fontSize: 12, marginTop: 12 },
  issueText: { backgroundColor: '#FFF8EB', borderRadius: 9, color: '#A85C00', fontSize: 12, fontWeight: '700', lineHeight: 18, marginTop: 12, padding: 10 },
  confirmedText: { color: ui.colors.success, fontSize: 14, fontWeight: '800', marginTop: 18 },
  buttonRow: { flexDirection: 'row', gap: 9, marginTop: 16 },
  editButton: { alignItems: 'center', borderColor: '#B7CDFC', borderRadius: 11, borderWidth: 1, flex: 1, paddingVertical: 11 },
  editButtonText: { color: ui.colors.primary, fontSize: 14, fontWeight: '800' },
  confirmButton: { alignItems: 'center', backgroundColor: ui.colors.primary, borderRadius: 11, flex: 1, paddingVertical: 11 },
  confirmButtonDisabled: { backgroundColor: ui.colors.disabled },
  confirmButtonText: { color: '#FFFFFF', fontSize: 14, fontWeight: '800' },
  discardButton: { alignItems: 'center', marginTop: 14, paddingVertical: 4 },
  discardButtonText: { color: ui.colors.danger, fontSize: 14, fontWeight: '800' },
});
