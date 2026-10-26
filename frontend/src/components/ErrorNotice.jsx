export default function ErrorNotice({ error }) {
  if (!error) return null;
  return <p className="notice error" role="alert">{error.message}</p>;
}
