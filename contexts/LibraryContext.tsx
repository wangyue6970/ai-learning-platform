import { createContext, useCallback, useContext, useEffect, useState, type Dispatch, type ReactNode, type SetStateAction } from 'react';
import { useAuth } from './AuthContext';
import { createLibrary as createLibraryRequest, deleteLibrary as deleteLibraryRequest, fetchLibraries, type Library, updateLibrary as updateLibraryRequest } from '../services/libraryApi';

type LibraryContextValue = {
  libraries: Library[];
  setLibraries: Dispatch<SetStateAction<Library[]>>;
  isLoading: boolean;
  error: string | null;
  reloadLibraries: () => Promise<void>;
  createLibrary: (name: string) => Promise<void>;
  updateLibrary: (id: string, name: string) => Promise<void>;
  deleteLibrary: (id: string) => Promise<void>;
};

const LibraryContext = createContext<LibraryContextValue | undefined>(undefined);

export function LibraryProvider({ children }: { children: ReactNode }) {
  const { accessToken, isRestoringSession } = useAuth();
  const [libraries, setLibraries] = useState<Library[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const reloadLibraries = useCallback(async () => {
    if (!accessToken) {
      setLibraries([]);
      setError(null);
      setIsLoading(false);
      return;
    }

    setIsLoading(true);
    setError(null);

    try {
      setLibraries(await fetchLibraries());
    } catch {
      setError('无法连接后端，请检查电脑和手机是否在同一 Wi-Fi。');
    } finally {
      setIsLoading(false);
    }
  }, []);

  const createLibrary = useCallback(async (name: string) => {
    const library = await createLibraryRequest(name);
    setLibraries((currentLibraries) => [...currentLibraries, library]);
  }, []);

  const updateLibrary = useCallback(async (id: string, name: string) => {
    const updatedLibrary = await updateLibraryRequest(id, name);
    setLibraries((currentLibraries) =>
      currentLibraries.map((library) => (library.id === id ? updatedLibrary : library))
    );
  }, []);

  const deleteLibrary = useCallback(async (id: string) => {
    await deleteLibraryRequest(id);
    setLibraries((currentLibraries) => currentLibraries.filter((library) => library.id !== id));
  }, [accessToken]);

  useEffect(() => {
    if (!isRestoringSession) {
      void reloadLibraries();
    }
  }, [isRestoringSession, reloadLibraries]);

  return (
    <LibraryContext.Provider value={{ libraries, setLibraries, isLoading, error, reloadLibraries, createLibrary, updateLibrary, deleteLibrary }}>
      {children}
    </LibraryContext.Provider>
  );
}

export function useLibraries() {
  const context = useContext(LibraryContext);

  if (!context) {
    throw new Error('useLibraries 必须在 LibraryProvider 内使用');
  }

  return context;
}
