export const CREATIVE_POLICY_VERSION = '2026-08-12'

export type CreativePolicyKey = 'reference-materials' | 'ai-output' | 'three-dimensional'

const policies: Record<CreativePolicyKey, { title: string; content: string }> = {
  'reference-materials': {
    title: '参考图片使用提醒',
    content: '请确认你上传的图片由你创作、拥有合法权利或已取得充分授权。图片中的人物肖像、商标、文物、景区或博物馆 IP、字体、图库素材等，均可能需要单独授权。你应自行承担因无权使用、侵权或违反他人权益产生的责任；平台仅按你的指令提供存储和 AI 处理服务。',
  },
  'ai-output': {
    title: 'AI生成内容提醒',
    content: '本次将生成或处理人工智能生成内容。AI结果可能不准确、重复或不具备商业使用条件，不代表平台对版权、授权、真实性或生产可行性作出保证。展示、投稿、销售、打样或生产前，请完成人工复核和权利核验，并按法律及渠道要求保留“AI生成”或同等含义标识。',
  },
  'three-dimensional': {
    title: '3D建模与预览提醒',
    content: '3D结果可能是原型、规格参考或模型草案，不保证尺寸、结构、强度、装配、开模、打印或量产一定可行。正式打样或生产前，必须由你、工厂或专业人员复核模型、尺寸、材质和工艺。平台不以 AI 结果替代专业设计、工程或质检意见。',
  },
}

/** 供原生页内确认层读取，避免部分微信环境未展示 showModal 时静默取消。 */
export function getCreativePolicy(key: CreativePolicyKey) { return policies[key] }

export function confirmCreativePolicy(key: CreativePolicyKey): Promise<boolean> {
  const policy = policies[key]
  return new Promise(resolve => {
    uni.showModal({
      title: policy.title,
      content: policy.content,
      showCancel: true,
      cancelText: '暂不继续',
      confirmText: '我已阅读并继续',
      success: result => resolve(Boolean(result.confirm)),
      fail: () => resolve(false),
    })
  })
}
