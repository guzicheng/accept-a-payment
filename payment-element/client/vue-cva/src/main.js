import { createApp, nextTick } from 'vue';
import { createRouter, createWebHistory } from 'vue-router'

import App from './App.vue';

import SrCheckoutForm from './components/SrCheckoutForm.vue'
import SrReturn from './components/SrReturn.vue'
import SrSubscriptionForm from './components/SrSubscriptionForm.vue';
import SrSubscriptionReturn from './components/SrSubscriptionReturn.vue';

const routes = [
  { 
    path: '/', 
    component: SrCheckoutForm, 
    meta: { 
      title: 'Payment'
     } 
  },
  { 
    path: '/subscription', 
    component: SrSubscriptionForm, 
    meta: { 
      title: 'Payment'
     } 
  },
  { 
    path: '/payment-return', 
    component: SrReturn, 
    meta: { title: 'Return' } 
  }, 
  { 
    path: '/sub-return', 
    component: SrSubscriptionReturn, 
    meta: { title: 'Return' } 
  }, 
]

const history = createWebHistory();

const router = new createRouter({
  history,
  routes
});

router.afterEach((to) => {
  nextTick(() => {
    document.title = to.meta.title || 'Payment';
  });
});

createApp(App).use(router).mount('#app');
