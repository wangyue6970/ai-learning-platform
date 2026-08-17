import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Alert, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import {
  getImportFileDrafts,
  QuestionDraft,
  QuestionDraftOption,
  updateImportFileDraft,
} from '../../../../../services/importApi';

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
      Alert.alert('请先选择正确答案', '直接点击下方正确选项即可，不需要手动输入 A、B、C。');
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
      Alert.alert('草稿已保存', '这道题仍是草稿，尚未进入正式题库。', [
        {
          text: '知道了',
          onPress: () => router.replace({
            pathname: '/library/[id]/drafts/[importFileId]',
            params: { id, importFileId },
          }),
        },
      ]);
    } catch (saveError) {
      const message = saveError instanceof Error ? saveError.message : '保存草稿失败，请稍后重试';
      Alert.alert('保存失败', message);
    } finally {
      setIsSaving(false);
    }
  }

  return (
    <ScrollView contentContainerStyle={styles.container} keyboardShouldPersistTaps="handled">
      <Pressable onPress={() => router.back()}>
        <Text style={styles.backText}>返回</Text>
      </Pressable>
      <Text style={styles.title}>编辑题目草稿</Text>
      <Text style={styles.subtitle}>保存后只更新草稿；最后确认入库前仍可以继续修改。</Text>

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
  container: { backgroundColor: '#F8FAFC', flexGrow: 1, padding: 20, paddingBottom: 40, paddingTop: 64 },
  backText: { color: '#2563EB', fontSize: 16 },
  title: { color: '#0F172A', fontSize: 28, fontWeight: '700', marginTop: 24 },
  subtitle: { color: '#64748B', fontSize: 15, lineHeight: 22, marginTop: 10 },
  loading: { marginTop: 42 },
  errorText: { color: '#B91C1C', marginTop: 28 },
  label: { color: '#0F172A', fontSize: 15, fontWeight: '700', marginTop: 24 },
  typeRow: { flexDirection: 'row', gap: 8, marginTop: 10 },
  typeButton: { borderColor: '#CBD5E1', borderRadius: 8, borderWidth: 1, flex: 1, paddingVertical: 10 },
  typeButtonSelected: { backgroundColor: '#2563EB', borderColor: '#2563EB' },
  typeButtonText: { color: '#475569', fontSize: 13, fontWeight: '700', textAlign: 'center' },
  typeButtonTextSelected: { color: '#FFFFFF' },
  input: { backgroundColor: '#FFFFFF', borderColor: '#CBD5E1', borderRadius: 8, borderWidth: 1, color: '#0F172A', fontSize: 16, marginTop: 10, minHeight: 48, padding: 12, textAlignVertical: 'top' },
  optionRow: { alignItems: 'center', flexDirection: 'row', gap: 10, marginTop: 10 },
  optionKey: { color: '#2563EB', fontSize: 16, fontWeight: '700', width: 20 },
  optionInput: { backgroundColor: '#FFFFFF', borderColor: '#CBD5E1', borderRadius: 8, borderWidth: 1, color: '#0F172A', flex: 1, fontSize: 16, minHeight: 46, padding: 10, textAlignVertical: 'top' },
  answerHint: { color: '#64748B', fontSize: 13, marginTop: 8 },
  answerOption: { alignItems: 'center', backgroundColor: '#FFFFFF', borderColor: '#CBD5E1', borderRadius: 8, borderWidth: 1, flexDirection: 'row', marginTop: 10, padding: 12 },
  answerOptionSelected: { backgroundColor: '#EFF6FF', borderColor: '#2563EB' },
  answerOptionKey: { color: '#2563EB', fontSize: 15, fontWeight: '700', marginRight: 10, width: 22 },
  answerOptionText: { color: '#0F172A', flex: 1, fontSize: 15 },
  answerOptionTextSelected: { color: '#1D4ED8', fontWeight: '700' },
  saveButton: { alignItems: 'center', backgroundColor: '#2563EB', borderRadius: 10, marginTop: 30, paddingVertical: 14 },
  saveButtonDisabled: { backgroundColor: '#93C5FD' },
  saveButtonText: { color: '#FFFFFF', fontSize: 16, fontWeight: '700' },
});
