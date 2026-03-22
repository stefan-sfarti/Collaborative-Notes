import { useEffect, useState } from "react";
import { Link as RouterLink, useNavigate } from "react-router-dom";
import { useAuth } from "../contexts/AuthContext";

function Register() {
  const { register, error, isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);

  async function handleLocalRegister(e: React.FormEvent) {
    e.preventDefault();
    if (!email || !password) return;
    try {
      setSubmitting(true);
      await register(email, password);
      navigate("/dashboard");
    } catch (err) {
      console.error("Local registration error:", err);
    } finally {
      setSubmitting(false);
    }
  }

  useEffect(() => {
    if (isAuthenticated) {
      navigate("/dashboard");
    }
  }, [isAuthenticated, navigate]);

  return (
    <div className="min-h-screen bg-gradient-to-br from-base-200 via-base-100 to-base-200 flex items-center justify-center px-4">
      <div className="w-full max-w-md">
        <div className="card bg-base-100/80 shadow-2xl border border-base-200 backdrop-blur">
          <div className="card-body space-y-4">
            <div className="flex flex-col items-center mb-2">
              <div className="avatar placeholder mb-3">
                <div className="bg-primary text-primary-content rounded-full w-16 shadow-lg flex items-center justify-center">
                  <span className="text-3xl">+</span>
                </div>
              </div>
              <h1 className="text-3xl font-extrabold bg-gradient-to-r from-primary to-secondary bg-clip-text text-transparent tracking-tight">
                CollabNotes
              </h1>
              <p className="mt-2 text-sm text-base-content/70">
                Sign up to start collaborating in real time
              </p>
            </div>

            {error && (
              <div className="alert alert-error">
                <span>{error}</span>
              </div>
            )}

            <form onSubmit={handleLocalRegister} className="space-y-3">
              <div className="form-control">
                <label className="label">
                  <span className="label-text">Email</span>
                </label>
                <input
                  type="email"
                  className="input input-bordered w-full"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                />
              </div>

              <div className="form-control">
                <label className="label">
                  <span className="label-text">Password</span>
                </label>
                <input
                  type="password"
                  className="input input-bordered w-full"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                />
              </div>

              <button
                type="submit"
                className="btn btn-primary btn-block mt-2"
                disabled={submitting}
              >
                {submitting && (
                  <span className="loading loading-spinner loading-xs mr-2" />
                )}
                Sign up
              </button>
            </form>

            <div className="mt-2 text-center text-sm text-base-content/70">
              Already have an account?{" "}
              <RouterLink
                to="/login"
                className="link link-primary font-semibold"
              >
                Log in
              </RouterLink>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

export default Register;
