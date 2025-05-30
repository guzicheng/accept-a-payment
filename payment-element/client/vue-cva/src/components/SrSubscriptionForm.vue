
<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { loadStripe } from '@stripe/stripe-js'

import SrMessages from "./SrMessages.vue";

// Props
// const props = defineProps({
//   priceId: {
//     type: String,
//     required: true
//   }
// })
const subPriceId = 'price_1RSz8SRsa5jn8xgzUSzHUXAc'

// 响应式状态
let stripe
let elements
let setupIntentClientSecret
let setupIntentCustomerId
const isLoading = ref(false)
const messages = ref([])

// 初始化
onMounted(async () => {
  try {
    const { publishableKey } = await fetch("/api/config").then((res) => res.json());
    stripe = await loadStripe(publishableKey)
    
    //向后端获取 SetupIntent client_secret
    const { clientSecret, customerId, error: backendError } = await fetch('/api/create-setup-intent').then(res => res.json())

    if (backendError) {
      messages.value.push(backendError.message);
    }
    else {
      setupIntentClientSecret = clientSecret
      setupIntentCustomerId = customerId
      messages.value.push(`Client secret returned.`);
    }

    elements = stripe.elements({
      clientSecret,
      appearance: {
        theme: 'stripe',
        variables: {
          colorPrimary: '#0570de',
        }
      }
    })
    const paymentElement = elements.create('payment')
    paymentElement.mount("#payment-element")
    const linkAuthenticationElement = elements.create("linkAuthentication");
    linkAuthenticationElement.mount("#link-authentication-element");

    isLoading.value = false;

  } catch (error) {
    messages.value.push('初始化支付失败: ' + error.message)
  }
})

// 处理表单提交
const handleSubmit = async (e) => {
  if (isLoading.value) {
    return;
  }

  isLoading.value = true;
  // e.preventDefault()f

  try {
    // 创建支付方式
    // const { error: paymentMethodError, paymentMethod } = 
    //   await stripe.createPaymentMethod({
    //     type: 'card',
    //     card: elements.getElement('card'),
    //   })

    // if (paymentMethodError) {
    //   throw new Error(paymentMethodError.message)
    // }

    // 创建订阅
    // const response = await fetch('/api/create-subscription', {
    //   method: 'POST',
    //   headers: { 'Content-Type': 'application/json' },
    //   body: JSON.stringify({
    //     paymentMethodId: paymentMethod.id,
    //     priceId: subPriceId
    //   })
    // })

    // 确认支付方式
    const result = await stripe.confirmSetup({
      elements,
      redirect: "if_required",
      confirmParams: {
        return_url: `${window.location.origin}/sub-return?price_id=${subPriceId}&customer_id=${setupIntentCustomerId}`
      }

      // payment_method: {
      //   card: stripe.elements().getElement('card'),
      //   billing_details: {
      //     name: '用户名称',
      //     email: 'user@example.com'
      //   }
      // }
    });

    if (result.error) {
      messages.value.push(result.error?.message);
    }
    else {
      const paymentMethodId = result.setupIntent.payment_method
      // 创建订阅
      const subscription = await fetch('/api/create-subscription', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ 
          customerId: setupIntentCustomerId,
          paymentMethodId: paymentMethodId,
          priceId: subPriceId 
        })
      }).then(res => res.json())

      if (subscription.error) {
        throw new Error(subscription.error.message)
      }

      //确认并完成付款
      const { error } = stripe.confirmPayment({
        clientSecret: subscription.clientSecret,
        confirmParams: {
          return_url: `${window.location.origin}/payment-return`
        }
      })
      
      messages.value.push('订阅成功：' + subscription.clientSecret)
    }

  } catch (error) {
    messages.value.push(error.message);
  } finally {
    isLoading.value = false
  }
}

onUnmounted(() => {
  if (elements) {
    elements.destroy()
  }
})
</script>

<template>
  <div class="subscription-form">
    <form @submit.prevent="handleSubmit">
      <div id="link-authentication-element" />
      <div id="payment-element"></div>
      <button type="submit" :disabled="isLoading">
        {{ isLoading ? '处理中...' : '订阅' }}
      </button>
      <sr-messages :messages="messages" />
    </form>
  </div>
</template>

<style scoped>
.subscription-form {
  max-width: 500px;
  margin: 0 auto;
  padding: 20px;
}

form {
  border: 1px solid #e0e0e0;
  padding: 20px;
  border-radius: 8px;
  background: white;
}

button {
  background: #5469d4;
  color: white;
  border: none;
  padding: 12px 16px;
  border-radius: 4px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  width: 100%;
  transition: all 0.2s ease;
}

button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.error-message {
  color: #dc2626;
  margin-top: 12px;
  text-align: center;
}
</style>