import { router } from 'expo-router';
import { useState } from 'react';
import { Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { useAuth } from '../contexts/AuthContext';
import { ui } from '../constants/ui';
import { useDialog } from '../components/AppDialog';

export default function LoginScreen() {
  const { login, registerAndLogin } = useAuth();
  const { showDialog } = useDialog();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [isRegisterMode, setIsRegisterMode] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function submit() {
    const trimmedUsername = username.trim();

    if (!trimmedUsername || !password) {
      showDialog({ title: '请输入用户名和密码', message: '填写完成后再继续登录或注册。', tone: 'warning' });
      return;
    }

    const wasRegisterMode = isRegisterMode;
    setIsSubmitting(true);

    try {
      if (wasRegisterMode) {
        await registerAndLogin(trimmedUsername, password);
      } else {
        await login(trimmedUsername, password);
      }
      router.replace('/');
    } catch (error) {
      const message = error instanceof Error ? error.message : '操作失败，请稍后重试';
      const registrationSucceeded = wasRegisterMode && message.startsWith('注册成功');
      if (registrationSucceeded) {
        setIsRegisterMode(false);
      }
      showDialog({
        title: registrationSucceeded ? '请登录' : wasRegisterMode ? '注册失败' : '登录失败',
        message,
        tone: registrationSucceeded ? 'success' : 'danger',
      });
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <View style={styles.container}>
      <View style={styles.brandRow}>
        <View style={styles.brandMark}><Text style={styles.brandMarkText}>AI</Text></View>
        <Text style={styles.brandName}>学习助手</Text>
      </View>
      <Text style={styles.title}>{isRegisterMode ? '创建你的账号' : '欢迎回来'}</Text>
      <Text style={styles.subtitle}>
        {isRegisterMode ? '注册后即可创建学习库、导入资料和刷题。' : '登录后继续管理学习库与题目。'}
      </Text>

      <Text style={styles.label}>用户名</Text>
      <TextInput
        autoCapitalize="none"
        autoCorrect={false}
        editable={!isSubmitting}
        placeholder="2 至 50 个字符"
        style={styles.input}
        value={username}
        onChangeText={setUsername}
      />

      <Text style={styles.label}>密码</Text>
      <TextInput
        editable={!isSubmitting}
        placeholder="至少 8 个字符"
        secureTextEntry
        style={styles.input}
        value={password}
        onChangeText={setPassword}
      />

      <Pressable disabled={isSubmitting} style={[styles.primaryButton, isSubmitting && styles.disabledButton]} onPress={() => void submit()}>
        <Text style={styles.primaryButtonText}>{isSubmitting ? '正在提交…' : isRegisterMode ? '注册并登录' : '登录'}</Text>
      </Pressable>

      <Pressable disabled={isSubmitting} onPress={() => setIsRegisterMode((current) => !current)}>
        <Text style={styles.switchModeText}>
          {isRegisterMode ? '已有账号？去登录' : '没有账号？去注册'}
        </Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { backgroundColor: ui.colors.background, flex: 1, justifyContent: 'center', padding: 24 },
  brandRow: { alignItems: 'center', flexDirection: 'row', marginBottom: 30 },
  brandMark: { alignItems: 'center', backgroundColor: ui.colors.primary, borderRadius: 13, height: 42, justifyContent: 'center', width: 42, ...ui.shadow },
  brandMarkText: { color: '#FFFFFF', fontSize: 15, fontWeight: '800' },
  brandName: { color: ui.colors.text, fontSize: 16, fontWeight: '800', marginLeft: 10 },
  title: { color: ui.colors.text, fontSize: 31, fontWeight: '800', letterSpacing: -0.8 },
  subtitle: { color: ui.colors.mutedText, fontSize: 15, lineHeight: 23, marginTop: 11 },
  label: { color: ui.colors.text, fontSize: 14, fontWeight: '800', marginTop: 28 },
  input: { backgroundColor: ui.colors.surface, borderColor: ui.colors.border, borderRadius: ui.radius.button, borderWidth: 1, color: ui.colors.text, fontSize: 16, marginTop: 9, paddingHorizontal: 16, paddingVertical: 15, ...ui.subtleShadow },
  primaryButton: { alignItems: 'center', backgroundColor: ui.colors.primary, borderRadius: ui.radius.button, marginTop: 32, paddingVertical: 16, ...ui.shadow },
  disabledButton: { backgroundColor: ui.colors.disabled },
  primaryButtonText: { color: '#FFFFFF', fontSize: 16, fontWeight: '700' },
  switchModeText: { color: ui.colors.primary, fontSize: 15, fontWeight: '800', marginTop: 22, textAlign: 'center' },
});
