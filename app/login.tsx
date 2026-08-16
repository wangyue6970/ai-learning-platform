import { router } from 'expo-router';
import { useState } from 'react';
import { Alert, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { useAuth } from '../contexts/AuthContext';

export default function LoginScreen() {
  const { login, registerAndLogin } = useAuth();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [isRegisterMode, setIsRegisterMode] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function submit() {
    const trimmedUsername = username.trim();

    if (!trimmedUsername || !password) {
      Alert.alert('请输入用户名和密码');
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
      Alert.alert(registrationSucceeded ? '请登录' : wasRegisterMode ? '注册失败' : '登录失败', message);
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <View style={styles.container}>
      <Text style={styles.title}>{isRegisterMode ? '注册账号' : '登录 AI 学习助手'}</Text>
      <Text style={styles.subtitle}>
        {isRegisterMode ? '注册后会自动登录。学习库归属限制将在下一步启用。' : '登录后可继续进入学习库和题目页面。'}
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
  container: { backgroundColor: '#F8FAFC', flex: 1, justifyContent: 'center', padding: 24 },
  title: { color: '#0F172A', fontSize: 28, fontWeight: '700' },
  subtitle: { color: '#64748B', fontSize: 15, lineHeight: 22, marginTop: 10 },
  label: { color: '#334155', fontSize: 14, fontWeight: '700', marginTop: 24 },
  input: { backgroundColor: '#FFFFFF', borderColor: '#CBD5E1', borderRadius: 10, borderWidth: 1, fontSize: 16, marginTop: 8, padding: 14 },
  primaryButton: { alignItems: 'center', backgroundColor: '#2563EB', borderRadius: 10, marginTop: 28, padding: 15 },
  disabledButton: { backgroundColor: '#93C5FD' },
  primaryButtonText: { color: '#FFFFFF', fontSize: 16, fontWeight: '700' },
  switchModeText: { color: '#2563EB', fontSize: 15, fontWeight: '700', marginTop: 20, textAlign: 'center' },
});
