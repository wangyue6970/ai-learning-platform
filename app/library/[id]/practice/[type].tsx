import { StatusBar } from 'expo-status-bar';
import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useState } from 'react';
import { Modal, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import {
  fetchPracticeQuestions,
  fetchWrongQuestions,
  submitAnswer as submitAnswerRequest,
  type PracticeQuestion,
  type SubmitAnswerResult,
} from '../../../../services/questionApi';
import { ui } from '../../../../constants/ui';
import { useDialog } from '../../../../components/AppDialog';

const questionTypeLabels: Record<PracticeQuestion['type'], string> = {
  single_choice: '单选题',
  multiple_choice: '多选题',
  true_false: '判断题',
};

type AnswerStatus = 'correct' | 'wrong';

export default function PracticeScreen() {
  const { id, type, mode } = useLocalSearchParams<{ id: string; type: string; mode?: string }>();
  const { showDialog } = useDialog();
  const [questions, setQuestions] = useState<PracticeQuestion[]>([]);
  const [selectedOptionIds, setSelectedOptionIds] = useState<string[]>([]);
  const [currentQuestionIndex, setCurrentQuestionIndex] = useState(0);
  const [answerResult, setAnswerResult] = useState<SubmitAnswerResult | null>(null);
  const [answerStatuses, setAnswerStatuses] = useState<Record<string, AnswerStatus>>({});
  const [isAnswerCardVisible, setIsAnswerCardVisible] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const isWrongPractice = mode === 'wrong';

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
        setQuestions(loadedQuestions.filter((question) => question.type === type));
      } catch {
        setError('无法加载题目，请检查后端是否已启动');
      } finally {
        setIsLoading(false);
      }
    }

    void loadQuestions();
  }, [id, isWrongPractice, type]);

  useEffect(() => {
    if (!answerResult?.correct) {
      return;
    }

    const timer = setTimeout(() => {
      if (currentQuestionIndex === questions.length - 1) {
        router.back();
        return;
      }

      setSelectedOptionIds([]);
      setAnswerResult(null);
      setCurrentQuestionIndex((currentIndex) => currentIndex + 1);
    }, 1000);

    return () => clearTimeout(timer);
  }, [answerResult, currentQuestionIndex, questions.length]);

  function selectOption(optionId: string) {
    if (answerResult || isSubmitting) {
      return;
    }

    if (currentQuestion.type === 'multiple_choice') {
      setSelectedOptionIds((currentIds) =>
        currentIds.includes(optionId)
          ? currentIds.filter((selectedId) => selectedId !== optionId)
          : [...currentIds, optionId]
      );
      return;
    }

    setSelectedOptionIds([optionId]);
    void submitSelectedAnswer([optionId]);
  }

  async function submitSelectedAnswer(selectedAnswer: string[]) {
    setIsSubmitting(true);
    try {
      const result = await submitAnswerRequest(id, currentQuestion.id, selectedAnswer);
      setAnswerResult(result);
      setAnswerStatuses((currentStatuses) => ({
        ...currentStatuses,
        [currentQuestion.id]: result.correct ? 'correct' : 'wrong',
      }));
    } catch {
      showDialog({ title: '提交失败', message: '请检查后端是否已启动，再重试。', tone: 'danger' });
    } finally {
      setIsSubmitting(false);
    }
  }

  function moveToNextQuestion() {
    if (currentQuestionIndex === questions.length - 1) {
      router.back();
      return;
    }

    setSelectedOptionIds([]);
    setAnswerResult(null);
    setCurrentQuestionIndex((currentIndex) => currentIndex + 1);
  }

  function moveToQuestion(questionIndex: number) {
    setCurrentQuestionIndex(questionIndex);
    setSelectedOptionIds([]);
    setAnswerResult(null);
    setIsAnswerCardVisible(false);
  }

  function getAnswerCardItemStyle(question: PracticeQuestion, questionIndex: number) {
    if (questionIndex === currentQuestionIndex) {
      return styles.answerCardCurrent;
    }

    return answerStatuses[question.id] === 'correct'
      ? styles.answerCardCorrect
      : answerStatuses[question.id] === 'wrong'
        ? styles.answerCardWrong
        : styles.answerCardUnanswered;
  }

  function getOptionStyle(optionId: string) {
    if (!answerResult) {
      return selectedOptionIds.includes(optionId) ? styles.optionSelected : styles.optionDefault;
    }

    if (answerResult.correctAnswer.includes(optionId)) {
      return styles.optionCorrect;
    }

    return selectedOptionIds.includes(optionId) ? styles.optionWrong : styles.optionDefault;
  }

  function getOptionMark(optionId: string) {
    if (!answerResult) {
      return '';
    }

    if (answerResult.correctAnswer.includes(optionId)) {
      return '✓';
    }

    return selectedOptionIds.includes(optionId) ? '×' : '';
  }

  if (isLoading) {
    return <View style={styles.centerState}><Text>题目加载中...</Text></View>;
  }

  if (error) {
    return <View style={styles.centerState}><Text>{error}</Text></View>;
  }

  if (questions.length === 0) {
    return <View style={styles.centerState}><Text>当前题型暂无题目。</Text></View>;
  }

  const currentQuestion = questions[currentQuestionIndex];
  const isLastQuestion = currentQuestionIndex === questions.length - 1;
  const correctCount = Object.values(answerStatuses).filter((status) => status === 'correct').length;
  const wrongCount = Object.values(answerStatuses).filter((status) => status === 'wrong').length;
  const unansweredCount = questions.length - correctCount - wrongCount;

  return (
    <View style={styles.screen}>
      <StatusBar style="dark" />
      <View style={styles.topBar}>
        <Pressable style={styles.backButton} onPress={() => router.back()}>
          <Text style={styles.backButtonText}>‹</Text>
        </Pressable>
        <View style={styles.modeSwitch}>
          <View style={styles.activeMode}><Text style={styles.activeModeText}>答题</Text></View>
          <View style={styles.inactiveMode}><Text style={styles.inactiveModeText}>背题</Text></View>
        </View>
        <Pressable style={styles.moreButton} onPress={() => showDialog({ title: '练习设置', message: '这个版本暂未提供额外练习设置。', tone: 'info' })}>
          <Text style={styles.moreButtonText}>•••</Text>
        </Pressable>
      </View>

      <ScrollView contentContainerStyle={styles.content}>
        <View style={styles.progressRow}>
          <Text style={styles.practiceTitle}>{isWrongPractice ? '错题练习' : '顺序练习'}</Text>
          <Text style={styles.progressText}><Text style={styles.currentProgress}>{currentQuestionIndex + 1}</Text> / {questions.length}</Text>
        </View>

        <View style={styles.questionHeader}>
          <Text style={styles.typeBadge}>{questionTypeLabels[currentQuestion.type]}</Text>
          <Text style={styles.favoritePlaceholder}>♡</Text>
        </View>
        <Text style={styles.stem}>{currentQuestion.stem}</Text>

        <View style={styles.optionList}>
          {currentQuestion.options.map((option) => (
            <Pressable
              key={option.id}
              disabled={Boolean(answerResult) || isSubmitting}
              onPress={() => selectOption(option.id)}
              style={[styles.optionCard, getOptionStyle(option.id)]}>
              <Text style={styles.optionKey}>{option.id}</Text>
              <Text style={styles.optionText}>{option.text}</Text>
              <Text style={answerResult?.correctAnswer.includes(option.id) ? styles.correctMark : styles.wrongMark}>
                {getOptionMark(option.id)}
              </Text>
            </Pressable>
          ))}
        </View>

        {currentQuestion.type === 'multiple_choice' && (
          <Pressable
            disabled={isSubmitting || Boolean(answerResult)}
            onPress={() => {
              if (selectedOptionIds.length === 0) {
                showDialog({ title: '请先选择答案', message: '选择一个或多个选项后，再提交答案。', tone: 'warning' });
                return;
              }
              void submitSelectedAnswer(selectedOptionIds);
            }}
            style={styles.submitButton}>
            <Text style={styles.submitButtonText}>{isSubmitting ? '提交中...' : '提交答案'}</Text>
          </Pressable>
        )}

        {answerResult && (
          <View style={[styles.resultCard, answerResult.correct ? styles.resultCorrect : styles.resultWrong]}>
            <Text style={[styles.resultTitle, answerResult.correct ? styles.resultCorrectText : styles.resultWrongText]}>
              {answerResult.correct ? '回答正确' : '回答错误'}
            </Text>
            <View style={styles.answerSummary}>
              <View style={styles.answerSummaryItem}>
                <Text style={styles.answerSummaryLabel}>参考答案</Text>
                <Text style={styles.referenceAnswer}>{answerResult.correctAnswer.join('、')}</Text>
              </View>
              <View style={styles.answerSummaryDivider} />
              <View style={styles.answerSummaryItem}>
                <Text style={styles.answerSummaryLabel}>我的答案</Text>
                <Text style={answerResult.correct ? styles.referenceAnswer : styles.myWrongAnswer}>{selectedOptionIds.join('、')}</Text>
              </View>
            </View>
            {answerResult.explanation && <Text style={styles.explanation}>解析：{answerResult.explanation}</Text>}
            {answerResult.removedFromWrongQuestions ? (
              <Text style={styles.statusHint}>连续答对两次，已移出错题集。</Text>
            ) : answerResult.correct && answerResult.consecutiveCorrectCount === 1 ? (
              <Text style={styles.statusHint}>还需答对 1 次，才能移出错题集。</Text>
            ) : null}
          </View>
        )}
      </ScrollView>

      <Modal animationType="slide" transparent visible={isAnswerCardVisible} onRequestClose={() => setIsAnswerCardVisible(false)}>
        <View style={styles.answerCardOverlay}>
          <View style={styles.answerCardSheet}>
            <View style={styles.answerCardTopBar}>
              <Pressable onPress={() => setIsAnswerCardVisible(false)}><Text style={styles.answerCardClose}>‹</Text></Pressable>
              <Text style={styles.answerCardTitle}>答题卡 {currentQuestionIndex + 1} / {questions.length}</Text>
              <View style={styles.answerCardTopSpacer} />
            </View>
            <View style={styles.answerCardLegend}>
              <Text style={styles.legendCorrect}>● 答对：{correctCount}</Text>
              <Text style={styles.legendWrong}>● 答错：{wrongCount}</Text>
              <Text style={styles.legendUnanswered}>● 未答：{unansweredCount}</Text>
            </View>
            <Text style={styles.answerCardSectionTitle}>{questionTypeLabels[currentQuestion.type]}</Text>
            <ScrollView contentContainerStyle={styles.answerCardGrid}>
              {questions.map((question, questionIndex) => {
                const isFilled = questionIndex === currentQuestionIndex || Boolean(answerStatuses[question.id]);
                return (
                  <Pressable
                    key={question.id}
                    onPress={() => moveToQuestion(questionIndex)}
                    style={[styles.answerCardNumber, getAnswerCardItemStyle(question, questionIndex)]}>
                    <Text style={[styles.answerCardNumberText, isFilled && styles.answerCardNumberTextFilled]}>
                      {questionIndex + 1}
                    </Text>
                  </Pressable>
                );
              })}
            </ScrollView>
            <Pressable style={styles.finishPracticeButton} onPress={() => router.back()}>
              <Text style={styles.finishPracticeButtonText}>结束练习</Text>
            </Pressable>
          </View>
        </View>
      </Modal>

      <View style={styles.bottomBar}>
        <Pressable disabled={currentQuestionIndex === 0} style={styles.bottomItem} onPress={() => moveToQuestion(currentQuestionIndex - 1)}>
          <Text style={currentQuestionIndex === 0 ? styles.bottomArrowDisabled : styles.bottomArrow}>‹</Text>
          <Text style={currentQuestionIndex === 0 ? styles.bottomLabelDisabled : styles.bottomLabel}>上一题</Text>
        </Pressable>
        <Pressable style={styles.bottomItem} onPress={() => setIsAnswerCardVisible(true)}>
          <Text style={styles.answerCardIcon}>▦</Text>
          <Text style={styles.bottomLabel}>答题卡</Text>
        </Pressable>
        <Pressable
          disabled={!answerResult || answerResult.correct}
          style={styles.bottomItem}
          onPress={moveToNextQuestion}>
          <Text style={!answerResult || answerResult.correct ? styles.bottomArrowDisabled : styles.bottomArrow}>›</Text>
          <Text style={!answerResult || answerResult.correct ? styles.bottomLabelDisabled : styles.bottomLabel}>
            {isLastQuestion ? '完成练习' : '下一题'}
          </Text>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: ui.colors.background },
  centerState: { flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: ui.colors.background },
  topBar: { height: 92, paddingTop: 42, paddingHorizontal: 20, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', backgroundColor: ui.colors.surface, borderBottomWidth: 1, borderBottomColor: ui.colors.border },
  backButton: { width: 42, height: 36, borderWidth: 1, borderColor: ui.colors.border, borderRadius: 18, alignItems: 'center', justifyContent: 'center' },
  backButtonText: { fontSize: 36, fontWeight: '300', color: ui.colors.text, lineHeight: 34, marginTop: -5 },
  modeSwitch: { flexDirection: 'row', backgroundColor: ui.colors.disabledSoft, borderRadius: 12, padding: 3 },
  activeMode: { backgroundColor: '#FFFFFF', borderRadius: 9, paddingHorizontal: 17, paddingVertical: 7, ...ui.subtleShadow },
  inactiveMode: { paddingHorizontal: 22, paddingVertical: 8 },
  activeModeText: { color: ui.colors.primary, fontSize: 16, fontWeight: '800' },
  inactiveModeText: { color: ui.colors.mutedText, fontSize: 16 },
  moreButton: { width: 42, height: 36, borderWidth: 1, borderColor: ui.colors.border, borderRadius: 18, alignItems: 'center', justifyContent: 'center' },
  moreButtonText: { color: ui.colors.text, fontSize: 15, fontWeight: '800', letterSpacing: 1 },
  content: { paddingBottom: 24 },
  progressRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: 20, paddingVertical: 20, borderBottomWidth: 1, borderBottomColor: ui.colors.border },
  practiceTitle: { color: ui.colors.text, fontSize: 19, fontWeight: '800' },
  progressText: { color: ui.colors.mutedText, fontSize: 16 },
  currentProgress: { color: ui.colors.primary, fontWeight: '800' },
  questionHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginTop: 18, paddingHorizontal: 20 },
  typeBadge: { backgroundColor: ui.colors.primarySoft, color: ui.colors.primary, borderRadius: 8, overflow: 'hidden', paddingHorizontal: 10, paddingVertical: 5, fontSize: 13, fontWeight: '800' },
  favoritePlaceholder: { color: ui.colors.mutedText, fontSize: 28, lineHeight: 30 },
  stem: { color: ui.colors.text, fontSize: 21, lineHeight: 31, fontWeight: '800', paddingHorizontal: 20, marginTop: 17 },
  optionList: { paddingHorizontal: 20, marginTop: 26, gap: 12 },
  optionCard: { minHeight: 64, borderWidth: 1, borderRadius: 14, paddingHorizontal: 16, flexDirection: 'row', alignItems: 'center' },
  optionDefault: { backgroundColor: ui.colors.surface, borderColor: ui.colors.border },
  optionSelected: { backgroundColor: ui.colors.primarySoft, borderColor: ui.colors.primary },
  optionCorrect: { backgroundColor: ui.colors.successSoft, borderColor: ui.colors.success },
  optionWrong: { backgroundColor: ui.colors.dangerSoft, borderColor: ui.colors.danger },
  optionKey: { color: ui.colors.primary, fontSize: 16, fontWeight: '800', width: 34 },
  optionText: { color: ui.colors.text, fontSize: 16, flex: 1, lineHeight: 23 },
  correctMark: { color: '#15803D', fontSize: 36, fontWeight: '700' },
  wrongMark: { color: '#DC2626', fontSize: 36, fontWeight: '700' },
  submitButton: { backgroundColor: ui.colors.primary, borderRadius: ui.radius.button, alignItems: 'center', marginHorizontal: 20, marginTop: 26, paddingVertical: 15, ...ui.shadow },
  submitButtonText: { color: '#FFFFFF', fontSize: 16, fontWeight: '800' },
  resultCard: { marginHorizontal: 20, marginTop: 22, padding: 16, borderRadius: ui.radius.card },
  resultCorrect: { backgroundColor: ui.colors.successSoft },
  resultWrong: { backgroundColor: ui.colors.dangerSoft },
  resultTitle: { fontSize: 22, fontWeight: '700' },
  resultCorrectText: { color: '#24A761' },
  resultWrongText: { color: '#E33D3D' },
  answerSummary: { flexDirection: 'row', marginTop: 16, paddingVertical: 12, backgroundColor: '#FFFFFF', borderRadius: 8 },
  answerSummaryItem: { flex: 1, alignItems: 'center' },
  answerSummaryDivider: { width: 1, backgroundColor: '#E5E7EB' },
  answerSummaryLabel: { color: '#8B93A1', fontSize: 15 },
  referenceAnswer: { color: '#24A761', fontSize: 25, fontWeight: '700', marginTop: 6 },
  myWrongAnswer: { color: '#E33D3D', fontSize: 25, fontWeight: '700', marginTop: 6 },
  explanation: { color: '#303744', fontSize: 17, lineHeight: 26, marginTop: 16 },
  statusHint: { color: '#337FEA', fontSize: 16, marginTop: 12 },
  bottomBar: { minHeight: 84, borderTopWidth: 1, borderTopColor: ui.colors.border, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-around', paddingBottom: 7, backgroundColor: ui.colors.surface },
  bottomItem: { minWidth: 72, alignItems: 'center' },
  bottomItemDisabled: { minWidth: 72, alignItems: 'center', opacity: 0.48 },
  bottomArrow: { color: '#596273', fontSize: 42, lineHeight: 38 },
  bottomArrowDisabled: { color: '#AAB0BA', fontSize: 42, lineHeight: 38 },
  answerCardIcon: { color: ui.colors.primary, fontSize: 28, lineHeight: 34 },
  bottomLabel: { color: '#4A5260', fontSize: 15, marginTop: 2 },
  bottomLabelDisabled: { color: '#9CA3AF', fontSize: 15, marginTop: 2 },
  answerCardOverlay: { flex: 1, backgroundColor: '#F6F8FC' },
  answerCardSheet: { flex: 1, paddingTop: 42 },
  answerCardTopBar: { height: 72, backgroundColor: '#3D87EB', flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 24 },
  answerCardClose: { color: '#FFFFFF', fontSize: 44, fontWeight: '200', lineHeight: 42, marginTop: -6 },
  answerCardTitle: { color: '#FFFFFF', fontSize: 24, fontWeight: '500' },
  answerCardTopSpacer: { width: 32 },
  answerCardLegend: { backgroundColor: '#FFFFFF', flexDirection: 'row', justifyContent: 'center', gap: 18, paddingVertical: 16 },
  legendCorrect: { color: '#657080', fontSize: 16 },
  legendWrong: { color: '#657080', fontSize: 16 },
  legendUnanswered: { color: '#657080', fontSize: 16 },
  answerCardSectionTitle: { color: '#171B22', fontSize: 25, fontWeight: '700', marginHorizontal: 24, marginTop: 34 },
  answerCardGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 15, paddingHorizontal: 24, paddingTop: 28, paddingBottom: 120 },
  answerCardNumber: { width: 48, height: 48, borderRadius: 24, alignItems: 'center', justifyContent: 'center' },
  answerCardUnanswered: { backgroundColor: '#D9DDE5' },
  answerCardCurrent: { backgroundColor: '#3D87EB' },
  answerCardCorrect: { backgroundColor: '#23B56C' },
  answerCardWrong: { backgroundColor: '#EF4444' },
  answerCardNumberText: { color: '#FFFFFF', fontSize: 18 },
  answerCardNumberTextFilled: { color: '#FFFFFF', fontWeight: '600' },
  finishPracticeButton: { position: 'absolute', left: 24, right: 24, bottom: 24, borderRadius: 12, backgroundColor: '#3D87EB', alignItems: 'center', paddingVertical: 16 },
  finishPracticeButtonText: { color: '#FFFFFF', fontSize: 21, fontWeight: '700' },
});
