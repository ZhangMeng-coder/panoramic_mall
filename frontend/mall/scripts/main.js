/* ============================================================================
   全景商城 · 前台（mall）风格样张 —— 渲染与交互
   ----------------------------------------------------------------------------
   普通脚本（不是 ES module）：这样用 file:// 双击 index.html 也能跑，
   不必起任何本地服务。

   数据来自 scripts/data.js（挂在 window 上）。
   所有「图片」都是 CSS 渐变占位，页面不发起任何网络请求。
   ========================================================================== */

;(function () {
  'use strict'

  /* 演示开关：true = 已登录态，false = 未登录态。
     改这里的初值，或点页面右上角「切换登录态」按钮实时切换。 */
  var DEMO_LOGGED_IN = false

  var HOTWORDS = window.MALL_HOTWORDS || []
  var CATS = window.MALL_CATS || []
  var BANNERS = window.MALL_BANNERS || []
  var GOODS = window.MALL_GOODS || []

  var AUTOPLAY_MS = 5000

  /* ---- 小工具 ---- */

  function $(sel) {
    return document.querySelector(sel)
  }

  /* 建元素：el('div', 'a b', '文本') */
  function el(tag, cls, text) {
    var n = document.createElement(tag)
    if (cls) n.className = cls
    if (text != null) n.textContent = text
    return n
  }

  /* 由色相生成占位渐变：grad(色相, 饱和度, 起始亮度, 结束亮度, 角度) */
  function grad(hue, sat, l1, l2, deg) {
    return (
      'linear-gradient(' +
      (deg || 135) +
      'deg, hsl(' +
      hue +
      ' ' +
      sat +
      '% ' +
      l1 +
      '%), hsl(' +
      ((hue + 28) % 360) +
      ' ' +
      sat +
      '% ' +
      l2 +
      '%))'
    )
  }

  /* 价格去掉多余小数：19.90 -> 19.9，8999.00 -> 8999 */
  function trimNum(n) {
    return String(+n.toFixed(2))
  }

  /* 角标样式：促销类实心主色，服务类白底主色字，其余白底灰字 */
  function tagClass(name) {
    if (name === '直降') return ''
    if (name === '包邮' || name === '次日达') return ' goods__tag--light'
    return ' goods__tag--neutral'
  }

  /* ==========================================================================
     ① 顶部用户条
     ========================================================================== */

  function renderTopbar() {
    var box = $('#topbarAccount')
    if (!box) return
    box.textContent = ''

    var greet = el('span', 'topbar__greet')

    if (DEMO_LOGGED_IN) {
      greet.appendChild(document.createTextNode('Hi，'))
      greet.appendChild(el('b', null, '张小明'))

      var out = el('a', 'topbar__logout', '退出')
      out.href = '#'

      box.appendChild(greet)
      box.appendChild(el('span', 'topbar__divider'))
      box.appendChild(out)
    } else {
      greet.appendChild(document.createTextNode('你好，'))

      var login = el('a', 'topbar__login', '请登录')
      login.href = '#'
      greet.appendChild(login)

      var reg = el('a', 'topbar__reg', '免费注册')
      reg.href = '#'

      box.appendChild(greet)
      box.appendChild(el('span', 'topbar__divider'))
      box.appendChild(reg)
    }
  }

  /* ==========================================================================
     ② 万能搜索长框
     ========================================================================== */

  function initSearch() {
    var form = $('#searchForm')
    var input = $('#searchInput')
    var hint = $('#searchHint')
    var hotList = $('#hotwords')
    if (!form || !input || !hint || !hotList) return

    function doSearch() {
      var kw = input.value.trim()
      hint.textContent = kw
        ? '（样张：搜索未接入，关键词「' + kw + '」）'
        : '（样张：输入关键词后回车，这里只是提示，不会跳转）'
    }

    HOTWORDS.forEach(function (w) {
      var a = el('a', null, w)
      a.href = '#'
      a.addEventListener('click', function (e) {
        e.preventDefault()
        input.value = w
        doSearch()
      })
      var li = el('li')
      li.appendChild(a)
      hotList.appendChild(li)
    })

    form.addEventListener('submit', function (e) {
      e.preventDefault()
      doSearch()
    })
  }

  /* ==========================================================================
     ③ 全分类展示
     ========================================================================== */

  function renderCats() {
    var grid = $('#catsGrid')
    if (!grid) return
    grid.textContent = ''

    CATS.forEach(function (c) {
      var li = el('li', 'cats__item')

      var icon = el('div', 'cats__icon', c.emoji)
      icon.style.background = grad(c.hue, 78, 95, 88)

      li.appendChild(icon)
      li.appendChild(el('div', 'cats__name', c.name))
      grid.appendChild(li)
    })
  }

  /* ==========================================================================
     ④ 大型滚动广告框
     ========================================================================== */

  function buildSlide(b) {
    var slide = el('div', 'carousel__slide')
    slide.style.background = grad(b.hue, 74, 52, 57, 120)

    slide.appendChild(el('span', 'carousel__kicker', b.kicker))
    slide.appendChild(el('h2', 'carousel__title', b.title))
    slide.appendChild(el('p', 'carousel__sub', b.sub))

    var cta = el('a', 'carousel__cta', b.cta)
    cta.href = '#'
    slide.appendChild(cta)

    return slide
  }

  function initCarousel() {
    var root = $('#carousel')
    var track = $('#carouselTrack')
    var dotsWrap = $('#carouselDots')
    var prev = $('#carouselPrev')
    var next = $('#carouselNext')
    if (!root || !track || !dotsWrap || !prev || !next) return

    var total = BANNERS.length
    var index = 0
    var timer = null

    if (!total) return

    var dots = []
    BANNERS.forEach(function (b, i) {
      track.appendChild(buildSlide(b))

      var dot = el('button', 'carousel__dot')
      dot.type = 'button'
      dot.setAttribute('aria-label', '第 ' + (i + 1) + ' 张')
      dot.addEventListener('click', function () {
        go(i)
        start()
      })
      dotsWrap.appendChild(dot)
      dots.push(dot)
    })

    /* 只有一张时不显示箭头，避免点了没反应 */
    if (total < 2) {
      prev.style.display = 'none'
      next.style.display = 'none'
    }

    function go(i) {
      index = (i + total) % total
      track.style.transform = 'translateX(' + -index * 100 + '%)'
      dots.forEach(function (d, n) {
        d.classList.toggle('is-active', n === index)
      })
    }

    function stop() {
      if (timer) {
        clearInterval(timer)
        timer = null
      }
    }

    function start() {
      stop()
      if (total < 2) return
      timer = setInterval(function () {
        go(index + 1)
      }, AUTOPLAY_MS)
    }

    prev.addEventListener('click', function () {
      go(index - 1)
      start()
    })
    next.addEventListener('click', function () {
      go(index + 1)
      start()
    })

    /* 悬停暂停：鼠标在广告框内时不自动轮播 */
    root.addEventListener('mouseenter', stop)
    root.addEventListener('mouseleave', start)

    go(0)
    start()
  }

  /* ==========================================================================
     ⑤ 用户信息展示框
     ========================================================================== */

  function renderUserCard() {
    var card = $('#usercard')
    if (!card) return
    card.textContent = ''

    card.appendChild(el('div', 'usercard__avatar', DEMO_LOGGED_IN ? '🙂' : '👋'))

    var main = el('div', 'usercard__main')
    var greet = el('div', 'usercard__greet')

    if (DEMO_LOGGED_IN) {
      greet.appendChild(document.createTextNode('下午好，'))
      greet.appendChild(el('b', null, '张小明'))
    } else {
      greet.appendChild(document.createTextNode('你好，游客'))
    }
    main.appendChild(greet)
    main.appendChild(
      el(
        'div',
        'usercard__sub',
        DEMO_LOGGED_IN
          ? '黄金会员 · 本月已省 ¥128，还有 2 张券即将过期'
          : '登录后享会员价、查看订单与优惠券，新人还能领 188 元礼包'
      )
    )
    card.appendChild(main)

    var cta = el('a', 'usercard__cta', DEMO_LOGGED_IN ? '查看我的订单' : '去登录')
    cta.href = '#'
    card.appendChild(cta)

    var perks = el('div', 'usercard__perks')
    var items = DEMO_LOGGED_IN
      ? [
          ['12', '优惠券'],
          ['2860', '积分'],
          ['34', '收藏']
        ]
      : [
          ['0', '优惠券'],
          ['0', '积分'],
          ['0', '收藏']
        ]

    items.forEach(function (it) {
      var perk = el('div', 'perk')
      perk.appendChild(
        el(
          'div',
          'perk__num' + (DEMO_LOGGED_IN ? '' : ' perk__num--muted'),
          it[0]
        )
      )
      perk.appendChild(el('div', 'perk__label', it[1]))
      perks.appendChild(perk)
    })
    card.appendChild(perks)
  }

  /* ==========================================================================
     ⑥ 热门商品列表
     ========================================================================== */

  /* 价格分三层字号：¥ 小、整数大、小数小 */
  function priceNode(price) {
    var parts = price.toFixed(2).split('.')
    var node = el('span', 'goods__price tnum')
    node.appendChild(el('span', 'goods__price-sym', '¥'))
    node.appendChild(el('span', 'goods__price-int', parts[0]))
    node.appendChild(el('span', 'goods__price-dec', '.' + parts[1]))
    return node
  }

  function renderGoods() {
    var grid = $('#goodsGrid')
    if (!grid) return
    grid.textContent = ''

    GOODS.forEach(function (g) {
      var li = el('li', 'goods__item')

      /* 商品图：CSS 渐变占位 + 中间一个占位文字 */
      var thumb = el('div', 'goods__thumb')
      thumb.style.background = grad(g.hue, 60, 91, 82)

      var label = el('div', 'goods__thumb-label', g.imgLabel)
      label.style.color = 'hsl(' + g.hue + ' 42% 32%)'
      thumb.appendChild(label)

      if (g.tags && g.tags.length) {
        var tags = el('div', 'goods__tags')
        g.tags.forEach(function (t) {
          tags.appendChild(el('span', 'goods__tag' + tagClass(t), t))
        })
        thumb.appendChild(tags)
      }
      li.appendChild(thumb)

      /* 正文：名称（两行截断）+ 价格 / 销量 */
      var body = el('div', 'goods__body')
      body.appendChild(el('div', 'goods__name clamp-2', g.name))

      var bottom = el('div', 'goods__bottom')
      var prices = el('div', 'goods__prices')
      prices.appendChild(priceNode(g.price))
      if (g.originPrice != null) {
        prices.appendChild(
          el('span', 'goods__origin tnum', '¥' + trimNum(g.originPrice))
        )
      }
      bottom.appendChild(prices)
      bottom.appendChild(
        el(
          'span',
          'goods__sales tnum',
          g.salesText === '0' ? '暂无成交' : g.salesText + '人付款'
        )
      )
      body.appendChild(bottom)
      li.appendChild(body)

      grid.appendChild(li)
    })
  }

  /* ==========================================================================
     演示开关：切换登录态（真实页面不会有这个按钮）
     ========================================================================== */

  function initToggle() {
    var btn = $('#demoToggle')
    if (!btn) return

    function sync() {
      btn.textContent = DEMO_LOGGED_IN ? '切回未登录态' : '切换登录态'
    }

    btn.addEventListener('click', function () {
      DEMO_LOGGED_IN = !DEMO_LOGGED_IN
      sync()
      renderTopbar()
      renderUserCard()
    })

    sync()
  }

  /* ---- 启动 ---- */

  function init() {
    renderTopbar()
    initSearch()
    renderCats()
    initCarousel()
    renderUserCard()
    renderGoods()
    initToggle()
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init)
  } else {
    init()
  }
})()
