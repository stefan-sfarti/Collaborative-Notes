import { useState, useEffect } from "react";

export function useTheme(): { theme: string; toggleTheme: () => void } {
  const [theme, setTheme] = useState(() => {
    return localStorage.getItem("theme") || "cupcake";
  });

  useEffect(() => {
    localStorage.setItem("theme", theme);
    document.documentElement.setAttribute("data-theme", theme);
  }, [theme]);

  const toggleTheme = () => {
    setTheme((prevTheme) => (prevTheme === "cupcake" ? "dark" : "cupcake"));
  };

  return { theme, toggleTheme };
}
