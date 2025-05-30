<script setup>
import { ref, onMounted, computed } from "vue";
import { useRoute } from "vue-router";
import { loadStripe } from "@stripe/stripe-js";

import SrMessages from "./SrMessages.vue";

const messages = ref([]);
const clientSecret = ref('');
const priceId = ref('');
const customerId = ref('');


const currentRoute = computed(() => {
  return useRoute().query;
});
clientSecret.value = currentRoute.value?.setup_intent_client_secret;
priceId.value = currentRoute.value?.price_id;
customerId.value = currentRoute.value?.customer_id;
let stripe;

onMounted(async () => {
  const { publishableKey } = await fetch("/api/config").then((res) => res.json());   
  stripe = await loadStripe(publishableKey);

  const {error, setupIntent} = await stripe.retrieveSetupIntent(clientSecret.value);

  if (error) {
    messages.value.push(error.message);
  }
  else {
    messages.value.push(`Payment ${setupIntent.status}: ${setupIntent.id}`)
    // 创建订阅
    // const subscription = await fetch('/api/create-subscription', {
    //     method: 'POST',
    //     headers: { 'Content-Type': 'application/json' },
    //     body: JSON.stringify({ 
    //       customerId: setupIntent.customer,
    //       paymentMethodId: setupIntent.payment_method,
    //       priceId: priceId.value
    //     })
    //   }).then(res => res.json())

    //   if (subscription.error) {
    //     messages.value.push(subscription.error.message)
    //   }
    //   else {
    //     messages.value.push('订阅成功！')
    //   }
  }
  
});

</script>

<template>
  <body>
    <main>
      <a href="/">home</a>
      <h1>Thank you!</h1>
      <sr-messages
        v-if="clientSecret"
        :messages="messages"
      />
    </main>
  </body>
</template>
