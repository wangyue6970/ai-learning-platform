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
    } catch (createError) {
      const message = createError instanceof Error
        ? createError.message
        : '请确认后端正在运行，并且手机和电脑在同一 Wi-Fi。';
      showDialog({ title: '创建失败', message, tone: 'warning', primaryLabel: '继续修改' });
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
                <Text style={styles.title}>我的学习库</Text>
                <Text style={styles.eyebrow}>高效整理 · 智能练习 · 轻松备考</Text>
              </View>
              <Pressable style={styles.logoutButton} onPress={confirmLogout}>
                <Text style={styles.logoutText}>退出</Text>
              </Pressable>
            </View>
            <View style={styles.tipBanner}>
              <View style={styles.tipTextArea}>
                <Text style={styles.tipTitle}>让复习更高效</Text>
                <Text style={styles.tipDescription}>整理资料 · 自动生成题库</Text>
              </View>
              <View style={styles.heroArtwork}>
                <View style={styles.heroBookTop} />
                <View style={styles.heroBookMiddle} />
                <View style={styles.heroBookBottom} />
                <View style={styles.heroCap}>
                  <View style={styles.heroCapTop} />
                  <View style={styles.heroCapTassel} />
                </View>
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
  container: { backgroundColor: ui.colors.background, flex: 1, paddingHorizontal: 18, paddingTop: 56 },
  loadingContainer: { alignItems: 'center', backgroundColor: ui.colors.background, flex: 1, justifyContent: 'center', padding: 24 },
  header: { marginBottom: 18 },
  headerTopRow: { alignItems: 'center', flexDirection: 'row', justifyContent: 'space-between' },
  eyebrow: { color: ui.colors.mutedText, fontSize: 12, fontWeight: '600', letterSpacing: 0.15, marginTop: 6 },
  title: { color: ui.colors.text, fontSize: 26, fontWeight: '800', letterSpacing: -0.9 },
  logoutButton: { alignItems: 'center', backgroundColor: '#EEF4FF', borderRadius: 12, justifyContent: 'center', minHeight: 38, paddingHorizontal: 12 },
  logoutText: { color: ui.colors.primary, fontSize: 13, fontWeight: '800' },
  tipBanner: { alignItems: 'center', backgroundColor: '#286DFF', borderRadius: 18, flexDirection: 'row', marginTop: 18, minHeight: 108, overflow: 'hidden', paddingHorizontal: 17, paddingVertical: 16, ...ui.shadow },
  tipTextArea: { flex: 1, zIndex: 1 },
  tipTitle: { color: '#FFFFFF', fontSize: 18, fontWeight: '800', letterSpacing: -0.3 },
  tipDescription: { color: '#D9E8FF', fontSize: 12, fontWeight: '600', marginTop: 8 },
  heroArtwork: { alignItems: 'center', height: 86, justifyContent: 'center', marginLeft: 8, position: 'relative', width: 102 },
  heroBookTop: { backgroundColor: '#D7E7FF', borderRadius: 5, height: 13, left: 28, position: 'absolute', top: 53, transform: [{ rotate: '-7deg' }], width: 58 },
  heroBookMiddle: { backgroundColor: '#8DBAFF', borderRadius: 5, height: 13, left: 22, position: 'absolute', top: 63, transform: [{ rotate: '4deg' }], width: 63 },
  heroBookBottom: { backgroundColor: '#EDF5FF', borderRadius: 5, height: 13, left: 25, position: 'absolute', top: 74, transform: [{ rotate: '-3deg' }], width: 62 },
  heroCap: { alignItems: 'center', height: 45, justifyContent: 'center', position: 'absolute', right: 22, top: 12, width: 54 },
  heroCapTop: { backgroundColor: '#153A91', borderRadius: 4, height: 14, transform: [{ rotate: '-17deg' }], width: 45 },
  heroCapTassel: { backgroundColor: '#B7D4FF', borderRadius: 4, height: 23, position: 'absolute', right: 3, top: 21, transform: [{ rotate: '20deg' }], width: 4 },
  libraryCard: { alignItems: 'center', backgroundColor: ui.colors.surface, borderColor: '#EEF1F6', borderRadius: 14, borderWidth: 1, flexDirection: 'row', marginBottom: 10, minHeight: 71, paddingHorizontal: 12, paddingVertical: 10, ...ui.subtleShadow },
  pressedCard: { opacity: 0.78, transform: [{ scale: 0.99 }] },
  folderIcon: { borderRadius: 12, height: 46, justifyContent: 'center', overflow: 'hidden', width: 46 },
  folderTab: { borderRadius: 5, height: 11, left: 8, position: 'absolute', top: 8, width: 20 },
  folderBody: { borderRadius: 7, height: 26, left: 6, opacity: 0.92, position: 'absolute', top: 16, width: 35 },
  libraryInfo: { flex: 1, marginLeft: 12 },
  libraryName: { color: ui.colors.text, fontSize: 15, fontWeight: '800' },
  libraryMeta: { color: ui.colors.mutedText, fontSize: 11, marginTop: 5 },
  chevron: { color: '#93A1B5', fontSize: 25, fontWeight: '300', lineHeight: 28, marginLeft: 8 },
  createButton: { alignItems: 'center', backgroundColor: '#286DFF', borderRadius: 15, marginBottom: 28, marginTop: 14, minHeight: 53, justifyContent: 'center', ...ui.shadow },
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
