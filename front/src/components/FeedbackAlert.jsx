export const FeedbackAlert = ({
    message,
    variant = "danger",
    role = "alert",
    id,
}) =>
    message ? (
        <div id={id} className={`alert alert-${variant} py-2 mb-3`} role={role}>
            {message}
        </div>
    ) : null;
