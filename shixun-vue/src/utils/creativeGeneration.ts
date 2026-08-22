/**
 * Normalize the shared creative-generation brief before it crosses the H5
 * boundary. Prompt policy still lives on the server; this adapter only makes
 * the request shape stable across the consumer page and the studio.
 */
export type CreativeGenerationPayload = Record<string, any> & {
  prompt?: string
  rawPrompt?: string
  productKey?: string
  productCategory?: string
  productType?: string
  material?: string
  productSize?: string
  negativePrompt?: string
  inputAssetId?: number | null
  refinement?: boolean
  refinementNote?: string
}

function clean(value: any, limit = 2400) {
  return String(value ?? '').trim().replace(/\s+/g, ' ').slice(0, limit)
}

function cleanPrompt(value: any, limit = 6000) {
  return String(value ?? '')
    .replace(/\r\n?/g, '\n')
    .split('\n')
    .map(line => line.trim().replace(/[ \t]+/g, ' '))
    .join('\n')
    .trim()
    .slice(0, limit)
}

export function buildCreativeGenerationPayload(input: CreativeGenerationPayload = {}) {
  const prompt = cleanPrompt(input.prompt, 6000)
  const suppliedRawPrompt = cleanPrompt(input.rawPrompt, 6000)
  const rawPrompt = suppliedRawPrompt || prompt
  const suppliedCategory = clean(input.productCategory, 160)
  const suppliedType = clean(input.productType, 160)
  const productCategory = suppliedCategory || suppliedType || '文创产品'
  const productType = suppliedType || productCategory
  const numericAssetId = Number(input.inputAssetId)
  return {
    ...input,
    prompt,
    rawPrompt,
    productKey: clean(input.productKey, 120),
    productCategory,
    productType,
    material: clean(input.material, 160),
    productSize: clean(input.productSize, 120),
    negativePrompt: cleanPrompt(input.negativePrompt, 4000),
    inputAssetId: Number.isFinite(numericAssetId) && numericAssetId > 0
      ? numericAssetId
      : null,
    refinement: input.refinement === true,
    refinementNote: cleanPrompt(input.refinementNote, 2400),
  }
}
