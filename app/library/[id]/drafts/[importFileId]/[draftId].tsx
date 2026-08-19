import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import {
  getImportFileDrafts,
  QuestionDraft,
  QuestionDraftOption,
  updateImportFileDraft,
} from '../../../../../services/importApi';
import { ui } from '../../../../../constants/ui';
import { useDialog } from '../../../../../components/AppDialog';

type DraftQuestionType = QuestionDraft['questionType'];

const questionTypes: { value: DraftQuestionType; label: string }[] = [
  { value: 'SINGLE_CHOICE', label: '单选题' },
  { value: 'MULTIPLE_CHOICE', label: '多选题' },
  { value: 'TRUE_FALSE', label: '判断题' },
];

export default function EditQuestionDraftScreen() {
  const { id, importFileId, draftId } = useLocalSearchParams<{
    id: string;
    importFileId: string;
    draftId: string;
  }>();
  const { showDialog } = useDialog();
  const [questionType, setQuestionType] = useState<DraftQuestionType>('SINGLE_CHOICE');
  const [stem, setStem] = useState('');
  const [options, setOptions] = useState<QuestionDraftOption[]>([]);
  const [selectedAnswerKeys, setSelectedAnswerKeys] = useState<string[]>([]);
  const [explanation, setExplanation] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    async function loadDraft() {
      setIsLoading(true);
      setError('');

      try {
        const foundDraft = (await getImportFileDrafts(id, importFileId)).find(
          (item) => String(item.id) === draftId
        );
        if (!foundDraft) {
          throw new Error('题目草稿不存在');
        }
        setQuestionType(foundDraft.questionType);
        setStem(foundDraft.stem);
        const editableOptions = foundDraft.questionType === 'TRUE_FALSE' && foundDraft.options.length === 0
          ? [
              { optionKey: 'TRUE', content: '正确', sortOrder: 1 },
              { optionKey: 'FALSE', content: '错误', sortOrder: 2 },
            ]
          : foundDraft.options;
        setOptions(editableOptions);
        setSelectedAnswerKeys(foundDraft.correctAnswer.filter((answer) =>
          editableOptions.some((option) => option.optionKey === answer)
        ));
        setExplanation(foundDraft.explanation || '');
      } catch (loadError) {
        setError(loadError instanceof Error ? loadError.message : '读取题目草稿失败，请稍后重试');
      } finally {
        setIsLoading(false);
      }
    }

    void loadDraft();
  }, [draftId, id, importFileId]);

  function updateOptionContent(optionKey: string, content: string) {
    setOptions((currentOptions) => currentOptions.map((option) =>
      option.optionKey === optionKey ? { ...option, content } : option
    ));
  }

  function toggleCorrectAnswer(optionKey: string) {
    if (questionType !== 'MULTIPLE_CHOICE') {
      setSelectedAnswerKeys([optionKey]);
      return;
    }

    setSelectedAnswerKeys((currentAnswers) => currentAnswers.includes(optionKey)
      ? currentAnswers.filter((answer) => answer !== optionKey)
      : [...currentAnswers, optionKey]
    );
  }

  async function saveDraft() {
    if (isSaving) {
      return;
    }

    if (selectedAnswerKeys.length === 0) {
      showDialog({ title: '请先选择正确答案', message: '直接点击下方正确选项即可，不需要手动输入 A、B、C。', tone: 'warning' });
      return;
    }
    const normalizedOptions = options.map((option, index) => ({
      ...option,
      content: option.content?.trim() || '',
      sortOrder: index + 1,
    }));

    setIsSaving(true);
    try {
      await updateImportFileDraft(id, importFileId, draftId, {
        questionType,
        stem: stem.trim(),
        correctAnswer: selectedAnswerKeys,
        explanation: explanation.trim() || null,
        knowledgePoints: [],
        options: normalizedOptions,
      });
      showDialog({
        title: '草稿已保存',
        message: '这道题仍是草稿，尚未进入正式题库。',
        tone: 'success',
        onPrimary: () => router.replace({
          pathname: '/library/[id]/drafts/[importFileId]',
          params: { id, importFileId },
        }),
      });
    } catch (saveError) {
      const message = saveError instanceof Error ? saveError.message : '保存草稿失败，请稍后重试';
      showDialog({ title: '保存失败', message, tone: 'warning', primaryLabel: '继续修改' });
    } finally {
      setIsSaving(false);
    }
  }

  return (
    <ScrollView contentContainerStyle={styles.container} keyboardShouldPersistTaps="handled">
      <Pressable onPress={() => router.back()}>
        <Text style={styles.backText}>返回</Text>
      </Pressable>
      <Text style={styles.title}>确认识别结果</Text>
      <Text style={styles.subtitle}>可修正题干、选项和正确答案；保存后再回到上一页确认入库。</Text>

      {isLoading && <ActivityIndicator color="#2563EB" size="large" style={styles.loading} />}
      {!!error && <Text style={styles.errorText}>{error}</Text>}
      {!isLoading && !error && (
        <View>
          <Text style={styles.label}>题型</Text>
          <View style={styles.typeRow}>
            {questionTypes.map((type) => (
              <Pressable
                key={type.value}
                style={[styles.typeButton, questionType === type.value && styles.typeButtonSelected]}
                onPress={() => setQuestionType(type.value)}
              >
                <Text style={[styles.typeButtonText, questionType === type.value && styles.typeButtonTextSelected]}>
                  {type.label}
                </Text>
              </Pressable>
            ))}
          </View>

          <Text style={styles.label}>题干</Text>
          <TextInput multiline style={styles.input} value={stem} onChangeText={setStem} />

          <Text style={styles.label}>选项</Text>
          {options.map((option) => (
            <View key={option.optionKey} style={styles.optionRow}>
              <Text style={styles.optionKey}>{option.optionKey}</Text>
              <TextInput
                multiline
                style={styles.optionInput}
                value={option.content || ''}
                onChangeText={(content) => updateOptionContent(option.optionKey, content)}
              />
            </View>
          ))}

          <Text style={styles.label}>正确答案</Text>
          <Text style={styles.answerHint}>
            {questionType === 'MULTIPLE_CHOICE' ? '多选题可点击多个选项。' : '点击一个选项即可设为正确答案。'}
          </Text>
          {options.map((option) => {
            const isSelected = selectedAnswerKeys.includes(option.optionKey);
            return (
              <Pressable
                key={`answer-${option.optionKey}`}
                style={[styles.answerOption, isSelected && styles.answerOptionSelected]}
                onPress={() => toggleCorrectAnswer(option.optionKey)}
              >
                <Text style={[styles.answerOptionKey, isSelected && styles.answerOptionTextSelected]}>
                  {isSelected ? '✓' : option.optionKey}
                </Text>
                <Text style={[styles.answerOptionText, isSelected && styles.answerOptionTextSelected]}>
                  {option.content || '未填写选项内容'}
                </Text>
              </Pressable>
            );
          })}

          <Text style={styles.label}>解析（可留空）</Text>
          <TextInput multiline style={styles.input} value={explanation} onChangeText={setExplanation} />

          <Pressable
            style={[styles.saveButton, isSaving && styles.saveButtonDisabled]}
            onPress={() => void saveDraft()}
          >
            <Text style={styles.saveButtonText}>{isSaving ? '正在保存…' : '保存草稿修改'}</Text>
          </Pressable>
        </View>
      )}
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
  label: { color: ui.colors.text, fontSize: 14, fontWeight: '800', marginTop: 23 },
  typeRow: { flexDirection: 'row', gap: 8, marginTop: 10 },
  typeButton: { backgroundColor: ui.colors.surface, borderColor: ui.colors.border, borderRadius: 10, borderWidth: 1, flex: 1, paddingVertical: 10 },
  typeButtonSelected: { backgroundColor: ui.colors.primary, borderColor: ui.colors.primary },
  typeButtonText: { color: ui.colors.mutedText, fontSize: 12, fontWeight: '800', textAlign: 'center' },
  typeButtonTextSelected: { color: '#FFFFFF' },
  input: { backgroundColor: ui.colors.surface, borderColor: '#DDE5F1', borderRadius: 11, borderWidth: 1, color: ui.colors.text, fontSize: 15, marginTop: 9, minHeight: 52, padding: 13, textAlignVertical: 'top' },
  optionRow: { alignItems: 'center', flexDirection: 'row', gap: 10, marginTop: 10 },
  optionKey: { color: ui.colors.primary, fontSize: 16, fontWeight: '800', width: 20 },
  optionInput: { backgroundColor: ui.colors.surface, borderColor: '#DDE5F1', borderRadius: 11, borderWidth: 1, color: ui.colors.text, flex: 1, fontSize: 15, minHeight: 48, padding: 11, textAlignVertical: 'top' },
  answerHint: { color: ui.colors.mutedText, fontSize: 13, marginTop: 8 },
  answerOption: { alignItems: 'center', backgroundColor: ui.colors.surface, borderColor: '#DDE5F1', borderRadius: 11, borderWidth: 1, flexDirection: 'row', marginTop: 9, padding: 13 },
  answerOptionSelected: { backgroundColor: ui.colors.primarySoft, borderColor: ui.colors.primary },
  answerOptionKey: { color: ui.colors.primary, fontSize: 15, fontWeight: '800', marginRight: 10, width: 22 },
  answerOptionText: { color: ui.colors.text, flex: 1, fontSize: 15 },
  answerOptionTextSelected: { color: ui.colors.primaryDark, fontWeight: '800' },
  saveButton: { alignItems: 'center', backgroundColor: ui.colors.primary, borderRadius: ui.radius.button, marginTop: 32, paddingVertical: 15, ...ui.shadow },
  saveButtonDisabled: { backgroundColor: ui.colors.disabled },
  saveButtonText: { color: '#FFFFFF', fontSize: 16, fontWeight: '800' },
});
