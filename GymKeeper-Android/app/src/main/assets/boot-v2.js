/* Final boot after all v2 overrides are installed. */
(function(){
  try{
    gkMigrate();
    render();
    if(setting('seen_version_2','0')!=='1'){
      gkTx(t=>gkSetSetting(t,'seen_version_2','1'));
      setTimeout(()=>showModal(`<h2>GymKeeper Offline 2.0</h2><p>Полный автономный режим обновлён:</p><ul><li>защита системным PIN/отпечатком;</li><li>полный TM/AMRAP с историей и отменой;</li><li>пошаговые миорепсы;</li><li>мини-тейпер и velocity stop;</li><li>кардио, пульс Huawei и аналитика восстановления;</li><li>проверяемые JSON-копии и миграции.</li></ul><button class="btn full" onclick="closeModal()">Понятно</button>`),350);
    }
  }catch(e){document.querySelector('#app').innerHTML=`<div class="empty"><h2>Не удалось обновить данные</h2><p>${esc(e.message)}</p><p>Восстановите последнюю JSON-копию.</p></div>`}
})();
