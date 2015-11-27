package com.wp.test.common;

import com.wp.mappers.ibatis.postgres.IItemsMapper;
import com.wp.model.enums.TypesEnum;
import com.wp.model.objects.Items;
import com.wp.model.objects.Nodes;
import com.wp.services.interfaces.*;
import com.wp.servicies.interfaces.ISectionsAndFiltersService;
import com.wp.web.forms.NoticesForm;
import com.wp.web.views.NoticeView;
import org.junit.*;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.transaction.TransactionConfiguration;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.Arrays;
import java.util.Date;

import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * Данный класс тестирует контроллер объявлений
 *
 * @author Ivan Yevsyukov
 * @e-mail ivan_yevsyukov@mail.ru
 */
@TransactionConfiguration(transactionManager = "transactionPostgresManager", defaultRollback = true)
@Transactional
@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
@ContextConfiguration(locations = {"classpath:/context_without_security_test.xml", "classpath:dispatcher-servlet-test.xml", "classpath:applicationContext-test.xml"})
public class NoticesControllerTest extends ATest{

    Logger log = LoggerFactory.getLogger(NoticesControllerTest.class);

    @Value("${section_notices}")
    private long DEFAULT_SECTION_ID = 0;

    @Value("${default_city_id}")
    private long DEFAULT_CITY_ID = 0;

    @Autowired
    private WebApplicationContext wac;

    @Autowired
    MockHttpServletRequest request;

    @Autowired
    MockHttpServletResponse response;
    private MockMvc mockMvc;
    protected MockHttpSession session;

    @Autowired
    IGraphService gs;

    @Autowired
    IUsersService _us;

    @Autowired
    IContentService _cs;

    @Autowired
    IIOService _io;

    @Autowired
    @Qualifier("graphService")
    IGraphService _gs;

    @Autowired
    IGarbageService _ut;

    @Autowired
    ISectionsAndFiltersService _sectionsService;

    @Autowired
    IItemsMapper _itemsMapper;



    public NoticesControllerTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {

    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
        this.mockMvc = webAppContextSetup(this.wac).build();

    }

    @After
    public void tearDown() {

    }

    @Test
    @Rollback
    @Ignore
    public void testComplex() throws Exception {

        // Получаем материнский узел
        Nodes node = _gs.getRootNode();

        // Инициализируем данные для объявления
        NoticesForm form = initNoticeField(node);

        // Создаем объявление
        MvcResult mr = mockMvc.perform(post("/" + node.getName() + "/notice").setForm(form).
                accept(MediaType.ALL).session(session)).andReturn();
        System.out.println(mr.getModelAndView().getModel().get("error"));
        assertTrue(mr.getResponse().getStatus() == 200);

        assertTrue(mr.getModelAndView().getModel().containsKey("item"));
        NoticeView notice = (NoticeView) mr.getModelAndView().getModel().get("item");
        assertTrue(notice.getId() > 0);

        //переходим на страницу объявления
        mr = mockMvc.perform(get("/notice/" + notice.getId()).accept(MediaType.TEXT_HTML).session(session)).andReturn();
        assertTrue(mr.getResponse().getStatus() == 200);

        //проверим созданные данные
        checkNoticeDataAfterSave(form, mr);

        //проверяем форму редактирования
        mr = mockMvc.perform(get("/notice/" + notice.getId() + "/edit").accept(MediaType.TEXT_HTML).session(session)).andReturn();
        assertTrue(mr.getResponse().getStatus() == 200);
        assertTrue(mr.getModelAndView().getModel().containsKey("notice"));
        notice = (NoticeView) mr.getModelAndView().getModel().get("notice");
        assertTrue(notice.getId() > 0);
        assertTrue(!notice.getTitle().equals(""));

        //сохраним объявление
        mr = mockMvc.perform(put("/" + node.getName() + "/notice/" + notice.getId() + "/edit").setForm(form).
                accept(MediaType.ALL).session(session)).andReturn();
        assertTrue(mr.getResponse().getStatus() == 200);
        assertTrue(mr.getModelAndView().getModel().containsKey("item"));
        notice = (NoticeView) mr.getModelAndView().getModel().get("item");
        assertTrue(notice.getId() > 0);

        //переходим на страницу объявления
        mr = mockMvc.perform(get("/notice/" + notice.getId()).accept(MediaType.TEXT_HTML).session(session)).andReturn();
        assertTrue(mr.getResponse().getStatus() == 200);

        //проверим созданные данные
        checkNoticeDataAfterSave(form, mr);

        //удалим объявление
        mr = mockMvc.perform(delete("/" + node.getName() + "/notices").param("notices_ids", "" + notice.getId()).accept(MediaType.APPLICATION_JSON).session(session)).andReturn();
        assertTrue(String.valueOf(mr.getResponse().getStatus()), mr.getResponse().getStatus() == 200);
        assertTrue(!mr.getModelAndView().getModel().containsKey("error"));

        //переходим на страницу объявлений
        mr = mockMvc.perform(get("/notice/" + notice.getId()).accept(MediaType.TEXT_HTML).session(session)).andReturn();
        assertTrue(mr.getResponse().getStatus() == 404);

    }

    private void checkNoticeDataAfterSave(NoticesForm form, MvcResult mr){

        NoticeView notice = (NoticeView) mr.getModelAndView().getModel().get("notice");

        assertTrue(notice.getTitle().equals(form.getTitle()));
        assertTrue(notice.getDescription().equals(form.getDescription()));
        assertTrue(notice.getContacts().equals(form.getContacts()));
        assertTrue(notice.getSection_id() == form.getSection_id());
        assertTrue(notice.getLocation().getId() == form.getLocation_id());
        assertTrue(notice.getImages().get(0).getId() == form.getImages().get(0));
    }

    private NoticesForm initNoticeField(Nodes node){

        NoticesForm notice = new NoticesForm();

        Items[] imagesArray = new Items[3];
        imagesArray[0] = _io.createItem(node, TypesEnum.IMAGE, "image");

        notice.setTitle("Заголовок");
        notice.setDescription("Описание");
        notice.setContacts("Контакты");
        notice.setSection_id(DEFAULT_SECTION_ID);
        notice.setLocation_id(DEFAULT_CITY_ID);
        notice.setDate_up(new Date());
        notice.setImages(Arrays.asList(imagesArray[0].getId()));

        return notice;

    }

}
