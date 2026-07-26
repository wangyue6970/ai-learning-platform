import { createContext, useContext, useState, type Dispatch, type ReactNode, type SetStateAction } from 'react';
import { initialLibraries, type Library } from '../data/libraries';

type LibraryContextValue = {
  libraries: Library[];
  setLibraries: Dispatch<SetStateAction<Library[]>>;
};

const LibraryContext = createContext<LibraryContextValue | undefined>(undefined);

export function LibraryProvider({ children }: { children: ReactNode }) {
  const [libraries, setLibraries] = useState<Library[]>(initialLibraries);

  return (
    <LibraryContext.Provider value={{ libraries, setLibraries }}>
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
