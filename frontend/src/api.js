export class ApiError extends Error {
	constructor(message, status = 0, fields = {}) {
		super(message);
		this.status = status;
		this.fields = fields;
	}
}

async function request(path, options = {}) {
	const controller = new AbortController();
	const timeout = setTimeout(() => controller.abort(), 10000);
	try {
		const response = await fetch(`/api/v1${path}`, {
			...options,
			headers: options.body ? { "Content-Type": "application/json" } : {},
			cache: "no-store",
			signal: controller.signal,
		});
		if (response.status === 204) return null;
		const text = await response.text();
		let body;
		try {
			body = JSON.parse(text);
		} catch {
			throw new ApiError(
				"El servicio no está disponible. Intenta nuevamente.",
				response.status,
			);
		}
		if (!response.ok) {
			throw new ApiError(
				body.detail || body.title || "No se pudo completar la operación.",
				response.status,
				body.errors || {},
			);
		}
		return body;
	} catch (error) {
		if (error instanceof ApiError) throw error;
		if (error.name === "AbortError") {
			throw new ApiError("La solicitud tardó demasiado. Intenta nuevamente.");
		}
		throw new ApiError(
			"No se pudo conectar con el servicio. Intenta nuevamente.",
		);
	} finally {
		clearTimeout(timeout);
	}
}

export const api = {
	listRules: () => request("/product-rules"),
	createRule: (rule) =>
		request("/product-rules", { method: "POST", body: JSON.stringify(rule) }),
	updateRule: (sku, rule) =>
		request(`/product-rules/${encodeURIComponent(sku)}`, {
			method: "PUT",
			body: JSON.stringify(rule),
		}),
	deleteRule: (sku) =>
		request(`/product-rules/${encodeURIComponent(sku)}`, { method: "DELETE" }),
	getPrice: (sku) => request(`/prices?${new URLSearchParams({ sku })}`),
};
