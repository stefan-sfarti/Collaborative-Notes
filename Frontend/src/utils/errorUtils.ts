import type { AxiosError } from "axios";
import type { ApiError } from "../types";

export function createApiError(error: AxiosError | unknown): ApiError {
  const axiosError = error as AxiosError<{ detail?: string; message?: string }>;
  const status = axiosError.response?.status ?? null;
  const data = axiosError.response?.data;
  const message =
    data?.detail ||
    data?.message ||
    axiosError.message ||
    "An unexpected error occurred";
  return {
    message,
    status,
    isConflict: status === 409,
  };
}
