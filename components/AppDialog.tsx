import { createContext, ReactNode, useContext, useMemo, useState } from 'react';
import { Modal, Pressable, StyleSheet, Text, View } from 'react-native';
import { ui } from '../constants/ui';

type DialogTone = 'info' | 'success' | 'warning' | 'danger';
type DialogVariant = 'primary' | 'danger';

type DialogOptions = {
  title: string;
  message?: string;
  tone?: DialogTone;
  primaryLabel?: string;
  primaryVariant?: DialogVariant;
  onPrimary?: () => void;
  secondaryLabel?: string;
  onSecondary?: () => void;
};

type DialogContextValue = {
  showDialog: (options: DialogOptions) => void;
};

const DialogContext = createContext<DialogContextValue | undefined>(undefined);

const toneIcon: Record<DialogTone, string> = {
  info: 'i',
  success: '✓',
  warning: '!',
  danger: '!',
};

export function DialogProvider({ children }: { children: ReactNode }) {
  const [dialog, setDialog] = useState<DialogOptions | null>(null);

  function closeDialog() {
    setDialog(null);
  }

  function runPrimary() {
    const action = dialog?.onPrimary;
    closeDialog();
    action?.();
  }

  function runSecondary() {
    const action = dialog?.onSecondary;
    closeDialog();
    action?.();
  }

  const contextValue = useMemo(() => ({ showDialog: setDialog }), []);
  const tone = dialog?.tone ?? 'info';
  const primaryVariant = dialog?.primaryVariant ?? (tone === 'danger' ? 'danger' : 'primary');

  return (
    <DialogContext.Provider value={contextValue}>
      {children}
      <Modal
        animationType="fade"
        transparent
        visible={dialog !== null}
        onRequestClose={closeDialog}>
        <View style={styles.backdrop}>
          <View style={styles.card}>
            <View style={[styles.icon, tone === 'success' && styles.successIcon, tone === 'warning' && styles.warningIcon, tone === 'danger' && styles.dangerIcon]}>
              <Text style={[styles.iconText, tone === 'success' && styles.successIconText, tone === 'warning' && styles.warningIconText, tone === 'danger' && styles.dangerIconText]}>
                {toneIcon[tone]}
              </Text>
            </View>
            <Text style={styles.title}>{dialog?.title}</Text>
            {!!dialog?.message && <Text style={styles.message}>{dialog.message}</Text>}
            <View style={styles.buttonRow}>
              {!!dialog?.secondaryLabel && (
                <Pressable style={styles.secondaryButton} onPress={runSecondary}>
                  <Text style={styles.secondaryButtonText}>{dialog.secondaryLabel}</Text>
                </Pressable>
              )}
              <Pressable
                style={[
                  styles.primaryButton,
                  dialog?.secondaryLabel ? styles.halfButton : styles.fullButton,
                  primaryVariant === 'danger' && styles.dangerButton,
                ]}
                onPress={runPrimary}>
                <Text style={styles.primaryButtonText}>{dialog?.primaryLabel ?? '知道了'}</Text>
              </Pressable>
            </View>
          </View>
        </View>
      </Modal>
    </DialogContext.Provider>
  );
}

export function useDialog() {
  const context = useContext(DialogContext);
  if (!context) {
    throw new Error('useDialog 必须在 DialogProvider 内使用');
  }
  return context;
}

const styles = StyleSheet.create({
  backdrop: { alignItems: 'center', backgroundColor: ui.colors.overlay, flex: 1, justifyContent: 'center', padding: 26 },
  card: { alignItems: 'center', backgroundColor: ui.colors.surface, borderRadius: 24, maxWidth: 420, padding: 24, width: '100%', ...ui.shadow },
  icon: { alignItems: 'center', backgroundColor: ui.colors.primarySoft, borderRadius: 28, height: 56, justifyContent: 'center', width: 56 },
  successIcon: { backgroundColor: ui.colors.successSoft },
  warningIcon: { backgroundColor: ui.colors.warningSoft },
  dangerIcon: { backgroundColor: ui.colors.dangerSoft },
  iconText: { color: ui.colors.primary, fontSize: 25, fontWeight: '800' },
  successIconText: { color: ui.colors.success },
  warningIconText: { color: ui.colors.warning },
  dangerIconText: { color: ui.colors.danger },
  title: { color: ui.colors.text, fontSize: 21, fontWeight: '800', marginTop: 16, textAlign: 'center' },
  message: { color: ui.colors.mutedText, fontSize: 15, lineHeight: 23, marginTop: 10, textAlign: 'center' },
  buttonRow: { flexDirection: 'row', gap: 10, marginTop: 24, width: '100%' },
  primaryButton: { alignItems: 'center', backgroundColor: ui.colors.primary, borderRadius: 13, justifyContent: 'center', minHeight: 48, ...ui.shadow },
  dangerButton: { backgroundColor: ui.colors.danger },
  halfButton: { flex: 1 },
  fullButton: { width: '100%' },
  primaryButtonText: { color: '#FFFFFF', fontSize: 15, fontWeight: '800' },
  secondaryButton: { alignItems: 'center', backgroundColor: ui.colors.disabledSoft, borderRadius: 13, flex: 1, justifyContent: 'center', minHeight: 48 },
  secondaryButtonText: { color: ui.colors.mutedText, fontSize: 15, fontWeight: '800' },
});
