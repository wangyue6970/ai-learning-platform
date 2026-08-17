import { StatusBar } from 'expo-status-bar';
import { Redirect, router, useFocusEffect } from 'expo-router';
import { useCallback, useState } from 'react';
import { FlatList, Modal, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { useLibraries } from '../contexts/LibraryContext';
import { useAuth } from '../contexts/AuthContext';
import { fetchPracticeQuestions, fetchWrongQuestions } from '../services/questionApi';
import { ui } from '../constants/ui';
import { useDialog } from '../components/AppDialog';

type LibraryQuestionSummary = {
  questionCount: number;
  wrongQuestionCount: number;
};

export default function IndexScreen() {
  const { accessToken, isRestoringSession } = useAuth();

  if (isRestoringSession) {
    return (
      <View style={styles.loadingContainer}>
        <Text style={styles.emptyText}>正在恢复登录状态…</Text>
      </View>
    );
  }

  if (!accessToken) {
    return <Redirect href="/login" />;
  }

  return <LibraryListScreen />;
}

function LibraryListScreen() {
  const { libraries, isLoading, error, createLibrary } = useLibraries();
  const { logout, username } = useAuth();
  const { showDialog } = useDialog();
  const [summaries, setSummaries] = useState<Record<string, LibraryQuestionSummary>>({});
  const [draftName, setDraftName] = useState('');
  const [isCreateModalVisible, setIsCreateModalVisible] = useState(false);

  const libraryColors = [
    { accent: '#2563EB', soft: '#E6F0FF' },
    { accent: '#16A36A', soft: '#E5F8EF' },
    { accent: '#7C5CE0', soft: '#F0ECFF' },
    { accent: '#E88422', soft: '#FFF1E1' },
  ];

  const reloadSummaries = useCallback(async () => {
    const summaryEntries = await Promise.all(
      libraries.map(async (library) => {
        const [questions, wrongQuestions] = await Promise.all([
          fetchPracticeQuestions(library.id),
          fetchWrongQuestions(library.id),
        ]);

        return [library.id, { questionCount: questions.length, wrongQuestionCount: wrongQuestions.length }] as const;
      })
    );

    setSummaries(Object.fromEntries(summaryEntries));
  }, [libraries]);

  useFocusEffect(
    useCallback(() => {
      void reloadSummaries().catch(() => setSummaries({}));
    }, [reloadSummaries])
  );

  async function createNewLibrary() {
    const trimmedName = draftName.trim();

    if (!trimmedName) {
      showDialog({ title: '请输入学习库名称', message: '给学习库起一个方便查找的名字。', tone: 'warning' });
      return;
    }

    try {
      await createLibrary(trimmedName);
      setDraftName('');
      setIsCreateModalVisible(false);
    } catch {
      showDialog({ title: '创建失败', message: '请确认后端正在运行，并且手机和电脑在同一 Wi-Fi。', tone: 'danger' });
    }
  }

  function confirmLogout() {
    showDialog({
      title: '退出登录？',
      message: '这会清除本机保存的登录状态，不会删除学习库数据。',
      tone: 'warning',
      secondaryLabel: '取消',
      primaryLabel: '退出登录',
      primaryVariant: 'danger',
      onPrimary: () => {
        void logout().catch(() => showDialog({ title: '退出登录失败', message: '无法清除本机登录状态，请稍后重试。', tone: 'danger' }));
      },
    });
  }

  return (
    <View style={styles.container}>
      <StatusBar style="dark" />
      <FlatList
        data={libraries}
        keyExtractor={(library) => library.id}
        renderItem={({ item, index }) => {
          const summary = summaries[item.id];

          return (
            <Pressable
              style={({ pressed }) => [styles.libraryCard, pressed && styles.pressedCard]}
              onPress={() => router.push(`/library/${item.id}`)}
            >
              <View
                style={[
                  styles.folderIcon,
                  {
                    backgroundColor: libraryColors[index % libraryColors.length].soft,
                  },
                ]}
              >
                <View
                  style={[
                    styles.folderTab,
                    { backgroundColor: libraryColors[index % libraryColors.length].accent },
                  ]}
                />
                <View
                  style={[
                    styles.folderBody,
                    { backgroundColor: libraryColors[index % libraryColors.length].accent },
                  ]}
                />
              </View>
              <View style={styles.libraryInfo}>
                <Text style={styles.libraryName}>{item.name}</Text>
                <Text style={styles.libraryMeta}>
                  {summary ? `共 ${summary.questionCount} 题 · ${summary.wrongQuestionCount} 道错题` : '题目统计加载中...'}
                </Text>
              </View>
              <Text style={styles.chevron}>›</Text>
            </Pressable>
          );
        }}
        ListHeaderComponent={
          <View style={styles.header}>
            <View style={styles.headerTopRow}>
              <View>
                <Text style={styles.eyebrow}>AI 学习助手</Text>
                <Text style={styles.title}>我的学习库</Text>
              </View>
              <Pressable onPress={confirmLogout}>
                <Text style={styles.logoutText}>退出登录</Text>
              </Pressable>
            </View>
            <Text style={styles.subtitle}>{username ? `你好，${username}。题目和错题会按学习库独立保存。` : '题目、错题和练习记录都会归属于一个学习库。'}</Text>
            <View style={styles.tipBanner}>
              <View style={styles.tipBadge}>
                <Text style={styles.tipBadgeText}>AI</Text>
              </View>
              <View style={styles.tipTextArea}>
                <Text style={styles.tipTitle}>把资料变成可练习的题库</Text>
                <Text style={styles.tipDescription}>图片导入、草稿确认、按题型刷题和错题巩固。</Text>
              </View>
            </View>
          </View>
        }
        ListFooterComponent={
          <Pressable style={({ pressed }) => [styles.createButton, pressed && styles.pressedButton]} onPress={() => setIsCreateModalVisible(true)}>
            <Text style={styles.createButtonText}>＋  新建学习库</Text>
          </Pressable>
        }
        ListEmptyComponent={
          <Text style={styles.emptyText}>{isLoading ? '正在加载学习库...' : error ?? '暂时没有学习库'}</Text>
        }
      />
      <Modal animationType="fade" transparent visible={isCreateModalVisible}>
        <View style={styles.modalBackdrop}>
          <View style={styles.modalCard}>
            <Text style={styles.modalTitle}>新建学习库</Text>
            <TextInput
              placeholder="例如：计算机网络"
              style={styles.input}
              value={draftName}
              onChangeText={setDraftName}
            />
            <Pressable style={styles.saveButton} onPress={() => void createNewLibrary()}>
              <Text style={styles.saveButtonText}>保存</Text>
            </Pressable>
            <Pressable onPress={() => {
              setDraftName('');
              setIsCreateModalVisible(false);
            }}>
              <Text style={styles.cancelText}>取消</Text>
            </Pressable>
          </View>
        </View>
      </Modal>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { backgroundColor: ui.colors.background, flex: 1, paddingHorizontal: 20, paddingTop: 58 },
  loadingContainer: { alignItems: 'center', backgroundColor: ui.colors.background, flex: 1, justifyContent: 'center', padding: 24 },
  header: { marginBottom: 20 },
  headerTopRow: { alignItems: 'center', flexDirection: 'row', justifyContent: 'space-between' },
  eyebrow: { color: ui.colors.primary, fontSize: 12, fontWeight: '700', letterSpacing: 0.7, marginBottom: 4 },
  title: { color: ui.colors.text, fontSize: 30, fontWeight: '800', letterSpacing: -0.8 },
  subtitle: { color: ui.colors.mutedText, fontSize: 14, lineHeight: 21, marginTop: 10 },
  logoutText: { color: ui.colors.mutedText, fontSize: 13, fontWeight: '700' },
  tipBanner: { alignItems: 'center', backgroundColor: ui.colors.primary, borderRadius: ui.radius.card, flexDirection: 'row', marginTop: 20, overflow: 'hidden', padding: 18, ...ui.shadow },
  tipBadge: { alignItems: 'center', backgroundColor: 'rgba(255,255,255,0.18)', borderColor: 'rgba(255,255,255,0.32)', borderRadius: 15, borderWidth: 1, height: 54, justifyContent: 'center', width: 54 },
  tipBadgeText: { color: '#FFFFFF', fontSize: 18, fontWeight: '800' },
  tipTextArea: { flex: 1, marginLeft: 14 },
  tipTitle: { color: '#FFFFFF', fontSize: 16, fontWeight: '800' },
  tipDescription: { color: '#DBEAFE', fontSize: 12, lineHeight: 18, marginTop: 5 },
  libraryCard: { alignItems: 'center', backgroundColor: ui.colors.surface, borderColor: ui.colors.border, borderRadius: ui.radius.card, borderWidth: 1, flexDirection: 'row', marginBottom: 12, padding: 15 },
  pressedCard: { opacity: 0.78, transform: [{ scale: 0.99 }] },
  folderIcon: { borderRadius: 14, height: 54, justifyContent: 'center', overflow: 'hidden', width: 54 },
  folderTab: { borderRadius: 5, height: 13, left: 9, position: 'absolute', top: 10, width: 23 },
  folderBody: { borderRadius: 8, height: 29, left: 7, opacity: 0.92, position: 'absolute', top: 18, width: 40 },
  libraryInfo: { flex: 1, marginLeft: 14 },
  libraryName: { color: ui.colors.text, fontSize: 17, fontWeight: '800' },
  libraryMeta: { color: ui.colors.mutedText, fontSize: 13, marginTop: 6 },
  chevron: { color: '#93A1B5', fontSize: 29, fontWeight: '300', lineHeight: 30, marginLeft: 10 },
  createButton: { alignItems: 'center', backgroundColor: ui.colors.primary, borderRadius: ui.radius.button, marginBottom: 28, marginTop: 10, paddingVertical: 16, ...ui.shadow },
  pressedButton: { backgroundColor: ui.colors.primaryDark, opacity: 0.9 },
  createButtonText: { color: '#FFFFFF', fontSize: 16, fontWeight: '800' },
  emptyText: { color: ui.colors.mutedText, marginTop: 20, textAlign: 'center' },
  modalBackdrop: { backgroundColor: ui.colors.overlay, flex: 1, justifyContent: 'center', padding: 22 },
  modalCard: { backgroundColor: ui.colors.surface, borderRadius: 22, padding: 22, ...ui.shadow },
  modalTitle: { color: ui.colors.text, fontSize: 21, fontWeight: '800' },
  input: { backgroundColor: ui.colors.background, borderColor: ui.colors.border, borderRadius: 12, borderWidth: 1, color: ui.colors.text, fontSize: 16, marginTop: 18, padding: 14 },
  saveButton: { alignItems: 'center', backgroundColor: ui.colors.primary, borderRadius: 12, marginTop: 14, padding: 14 },
  saveButtonText: { color: '#FFFFFF', fontWeight: '800' },
  cancelText: { color: ui.colors.mutedText, fontWeight: '800', marginTop: 16, textAlign: 'center' },
});
