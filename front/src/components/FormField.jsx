import { FaEye, FaEyeSlash } from "react-icons/fa6";

export const FormField = ({
    icon: Icon,
    id,
    label,
    type = "text",
    value,
    onChange,
    autoComplete,
    minLength,
    maxLength,
    toggle,
}) => (
    <div className="input-group field-group mb-3">
        <span className="input-group-text bg-white">
            <Icon aria-hidden="true" />
        </span>
        <input
            id={id}
            className="form-control border-start-0"
            type={type}
            name={id}
            placeholder={label}
            aria-label={label}
            value={value}
            onChange={onChange}
            autoComplete={autoComplete}
            minLength={minLength}
            maxLength={maxLength}
            required
        />
        {toggle && (
            <button
                className="btn btn-outline-secondary field-toggle"
                type="button"
                onClick={toggle.action}
                aria-label={
                    toggle.visible ? "Ocultar contraseña" : "Mostrar contraseña"
                }
            >
                {toggle.visible ? <FaEyeSlash /> : <FaEye />}
            </button>
        )}
    </div>
);
